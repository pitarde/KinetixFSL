// ============================================================
// kinetix-upload — Cloudflare Worker
//
// Two jobs:
//   POST /                      multipart upload from the Android app → R2
//   GET  /p/{postId}            public web page for a shared post
//   GET  /c/{communityId}       public web page for a shared community
//   GET  /u/{userId}           public web page for a shared profile
//   GET  /.well-known/assetlinks.json   Android App Links verification
//
// This is the whole Worker. Paste it over the Cloudflare editor's contents.
//
// Environment variables (Worker → Settings → Variables and Secrets):
//   KINETIX_BUCKET        R2 bucket binding          (already set up)
//   PUBLIC_BUCKET_URL     public base URL for R2     (already set up)
//   FIREBASE_PROJECT_ID   kinetixfsl-73d88           (new)
//   FIREBASE_API_KEY      Firebase Web API key       (new)
//   ANDROID_CERT_SHA256   signing fingerprint(s),
//                         comma-separated            (new)
// ============================================================

export default {
  async fetch(request, env) {
    // --- CORS preflight ------------------------------------------------
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: corsHeaders(),
      });
    }

    // --- Shared post pages ---------------------------------------------
    // Must come before the POST guard below, because these routes are GETs.
    // Returns null for anything that isn't a share route, so uploads fall
    // through untouched.
    const shared = await handleShareRoutes(request, env);
    if (shared) return shared;

    // --- Only accept POST ----------------------------------------------
    if (request.method !== "POST") {
      return jsonResponse(405, { error: "Method not allowed" });
    }

    // --- Deleting a post's media ---------------------------------------
    if (new URL(request.url).pathname === "/delete-media") {
      return handleDeleteMedia(request, env);
    }

    try {
      // --- Parse the multipart form ------------------------------------
      const formData = await request.formData();
      const file = formData.get("file");

      if (!file || !(file instanceof File)) {
        return jsonResponse(400, { error: "No file provided" });
      }

      // --- Determine folder from resource_type -------------------------
      const resourceType = formData.get("resource_type") || "image";
      const folder = resourceType === "video" ? "videos" : "images";

      // --- Generate a unique filename ----------------------------------
      const timestamp = Date.now();
      const random = Math.random().toString(36).substring(2, 10);
      const ext = getExtension(file.name, file.type);
      const key = `${folder}/${timestamp}-${random}${ext}`;

      // --- Upload to R2 ------------------------------------------------
      await env.KINETIX_BUCKET.put(key, file.stream(), {
        httpMetadata: {
          contentType: file.type || "application/octet-stream",
          // Every key is unique (timestamp + random), so an object at a given
          // URL never changes and can be cached forever. Without this header
          // Cloudflare's edge won't cache the file and the Android client
          // re-downloads it on every scroll — barely noticeable on WiFi,
          // painful on mobile data.
          cacheControl: "public, max-age=31536000, immutable",
        },
      });

      // --- Return the public URL ---------------------------------------
      const publicUrl = `${env.PUBLIC_BUCKET_URL}/${key}`;

      return jsonResponse(200, {
        secure_url: publicUrl,
        key: key,
        size: file.size,
      });
    } catch (err) {
      return jsonResponse(500, { error: err.message || "Upload failed" });
    }
  },
};

// --- Deleting a post's media -------------------------------------------

/**
 * POST /delete-media
 *   headers: x-kinetix-key: <DELETE_SECRET>
 *   body:    { "postId": "...", "keys": ["images/123-abc.webp", ...] }
 *
 * Called by the app just *before* it removes the post document, so the post
 * is still readable here and every key can be checked against it. A key that
 * doesn't appear in that post's own URLs is refused — so even with the shared
 * secret, this endpoint can't be turned into "delete anything in the bucket".
 *
 * SECURITY NOTE: the shared secret ships inside the APK and can be extracted
 * by anyone willing to unpack it. The ownership check above is what limits the
 * damage. To close it properly, send the caller's Firebase ID token instead and
 * verify it here against Google's public keys, then compare the uid to the
 * post's authorId. See web/SETUP.md.
 */
async function handleDeleteMedia(request, env) {
  const secret = env.DELETE_SECRET;
  if (!secret || request.headers.get("x-kinetix-key") !== secret) {
    return jsonResponse(401, { error: "Unauthorized" });
  }

  let payload;
  try {
    payload = await request.json();
  } catch (_) {
    return jsonResponse(400, { error: "Invalid JSON" });
  }

  const postId = payload && payload.postId;
  const keys = (payload && payload.keys) || [];
  if (!postId || !Array.isArray(keys) || keys.length === 0) {
    return jsonResponse(400, { error: "postId and keys are required" });
  }

  // Collect every URL the post references, so we can confirm ownership.
  const post = await fetchPost(postId, env);
  if (!post) {
    return jsonResponse(404, { error: "Post not found" });
  }
  // Images attached to comments belong to the post too, and go with it when
  // it's deleted — so they have to count as owned or they'd be refused.
  const commentUrls = await fetchCommentImageUrls(postId, env);

  const owned = new Set(
    collectPostUrls(post)
      .concat(commentUrls)
      .map(keyFromUrl)
      .filter(Boolean),
  );

  const deleted = [];
  const refused = [];

  for (const key of keys) {
    if (!owned.has(key)) {
      refused.push(key);
      continue;
    }
    try {
      await env.KINETIX_BUCKET.delete(key);
      deleted.push(key);
    } catch (_) {
      refused.push(key);
    }
  }

  return jsonResponse(200, { deleted: deleted.length, refused: refused.length });
}

/**
 * Every image attached to the post's comments.
 *
 * Paged, because a post can carry more comments than one REST response
 * returns and a missed page would mean a refused delete.
 */
async function fetchCommentImageUrls(postId, env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  const apiKey = env.FIREBASE_API_KEY;
  if (!projectId || !apiKey) return [];

  const base =
    `https://firestore.googleapis.com/v1/projects/${projectId}` +
    `/databases/(default)/documents/posts/${encodeURIComponent(postId)}/comments`;

  const urls = [];
  let pageToken = "";

  for (let page = 0; page < 20; page++) {
    let endpoint = `${base}?key=${apiKey}&pageSize=300`;
    if (pageToken) endpoint += `&pageToken=${encodeURIComponent(pageToken)}`;

    let response;
    try {
      response = await fetch(endpoint);
    } catch (_) {
      break;
    }
    if (!response.ok) break;

    const body = await response.json();
    for (const doc of body.documents || []) {
      const imageUrl = readString(doc.fields && doc.fields.imageUrl);
      if (imageUrl) urls.push(imageUrl);
    }

    pageToken = body.nextPageToken || "";
    if (!pageToken) break;
  }

  return urls;
}

/** Every media URL on a post, across the new and legacy fields. */
function collectPostUrls(post) {
  const urls = [post.imageUrl, post.videoUrl, post.previewUrl];
  for (const item of post.media || []) {
    urls.push(item.url, item.thumbUrl);
  }
  return urls.filter((u) => typeof u === "string" && u.length > 0);
}

/** "https://host/images/123-abc.webp" -> "images/123-abc.webp" */
function keyFromUrl(url) {
  try {
    return new URL(url).pathname.replace(/^\/+/, "");
  } catch (_) {
    return null;
  }
}

// --- Upload helpers ----------------------------------------------------

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };
}

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
    },
  });
}

function getExtension(filename, mimeType) {
  // Try from filename first
  if (filename) {
    const dot = filename.lastIndexOf(".");
    if (dot !== -1) return filename.substring(dot);
  }
  // Fall back to MIME type
  const map = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/gif": ".gif",
    "video/mp4": ".mp4",
    "video/3gpp": ".3gp",
    "video/webm": ".webm",
  };
  return map[mimeType] || "";
}

// ============================================================
// Shared post pages
//
// IMPORTANT — Firestore rules: the page below reads posts without signing
// in. Your rules must allow public reads of the posts collection, e.g.
//
//     match /posts/{postId} {
//       allow read: if true;
//       ...
//     }
//
// If reads stay locked to signed-in users, every shared link renders
// "Post unavailable" in a browser. See web/SETUP.md.
// ============================================================

const ANDROID_PACKAGE = "com.example.kinetixfsl";
const APP_NAME = "Kinetix FSL";

/** Must match ShareLinks.HOST in the Android app. Used for og:url. */
const SHARE_HOST = "kinetix-upload.pitardeken2024.workers.dev";

/** URL prefix this Worker serves bucket objects under. */
const MEDIA_PATH = "/f/";

/**
 * Answers the share routes. Returns null for every other request so the
 * upload handler above can take it.
 */
async function handleShareRoutes(request, env) {
  const url = new URL(request.url);

  // Media served straight off the R2 binding.
  //
  // The bucket's own pub-*.r2.dev hostname is unreachable on some mobile
  // carriers — it's free object storage that gets abused, so networks filter
  // it, and Cloudflare only intends it for development anyway. Serving through
  // the Worker's own domain avoids that entirely, and means the bucket needn't
  // be publicly readable at all.
  if (url.pathname.startsWith(MEDIA_PATH)) {
    return serveMedia(request, env, decodeURIComponent(url.pathname.slice(MEDIA_PATH.length)));
  }

  if (url.pathname === "/.well-known/assetlinks.json") {
    return assetLinksResponse(env);
  }

  if (url.pathname.startsWith("/p/")) {
    const postId = url.pathname.slice("/p/".length).split("/")[0];
    if (!postId) return null;
    return postPageResponse(postId, env);
  }

  if (url.pathname.startsWith("/c/")) {
    const communityId = url.pathname.slice("/c/".length).split("/")[0];
    if (!communityId) return null;
    return communityPageResponse(communityId, env);
  }

  if (url.pathname.startsWith("/u/")) {
    const userId = url.pathname.slice("/u/".length).split("/")[0];
    if (!userId) return null;
    return userPageResponse(userId, env);
  }

  return null;
}

// --- Serving media ------------------------------------------------------

/**
 * Streams one object out of the bucket.
 *
 * Range requests are honoured because ExoPlayer relies on them: it seeks to
 * read an MP4's index before playing, and without 206 support a video would
 * have to download in full before the first frame.
 */
async function serveMedia(request, env, key) {
  if (!key) return new Response("Not found", { status: 404 });
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response("Method not allowed", { status: 405 });
  }

  let object;
  try {
    object = await env.KINETIX_BUCKET.get(key, {
      range: request.headers,
      onlyIf: request.headers,
    });
  } catch (_) {
    return new Response("Not found", { status: 404 });
  }

  if (object === null) return new Response("Not found", { status: 404 });

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("etag", object.httpEtag);
  headers.set("accept-ranges", "bytes");
  // Keys are unique per upload, so an object at a URL never changes.
  headers.set("cache-control", "public, max-age=31536000, immutable");
  headers.set("access-control-allow-origin", "*");

  // No body means the conditional request matched — nothing to send.
  if (!("body" in object) || object.body === undefined) {
    return new Response(null, { status: 304, headers });
  }

  if (object.range && typeof object.range.offset === "number") {
    const start = object.range.offset;
    const end = start + (object.range.length || 0) - 1;
    headers.set("content-range", `bytes ${start}-${end}/${object.size}`);
    return new Response(object.body, { status: 206, headers });
  }

  return new Response(object.body, { status: 200, headers });
}

/**
 * Rewrites a stored pub-*.r2.dev URL onto this Worker.
 *
 * Posts created before the switch have the old hostname baked into Firestore,
 * so rewriting on the way out fixes them without a data migration.
 */
function throughWorker(url) {
  if (typeof url !== "string" || !url) return url;
  try {
    const parsed = new URL(url);
    if (!parsed.hostname.endsWith(".r2.dev")) return url;
    return `https://${SHARE_HOST}${MEDIA_PATH}${parsed.pathname.replace(/^\/+/, "")}`;
  } catch (_) {
    return url;
  }
}

// --- Android App Links -------------------------------------------------

function assetLinksResponse(env) {
  // Comma-separated so the debug and release certs can both be listed —
  // you need the debug one to test on your own device, and the release one
  // for the build you ship.
  const fingerprints = String(env.ANDROID_CERT_SHA256 || "")
    .split(",")
    .map((f) => f.trim().toUpperCase())
    .filter(Boolean);

  const body = JSON.stringify(
    [
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: ANDROID_PACKAGE,
          sha256_cert_fingerprints: fingerprints,
        },
      },
    ],
    null,
    2,
  );

  return new Response(body, {
    headers: {
      "content-type": "application/json",
      "cache-control": "public, max-age=3600",
    },
  });
}

// --- The post page -----------------------------------------------------

async function postPageResponse(postId, env) {
  const post = await fetchPost(postId, env);

  if (!post) {
    return htmlResponse(renderMissing(), 404);
  }

  return htmlResponse(renderPost(postId, post), 200);
}

/**
 * Reads posts/{postId} through the Firestore REST API and flattens
 * Firestore's typed-value wrappers into plain JS values.
 */
async function fetchPost(postId, env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  const apiKey = env.FIREBASE_API_KEY;
  if (!projectId || !apiKey) return null;

  const endpoint =
    `https://firestore.googleapis.com/v1/projects/${projectId}` +
    `/databases/(default)/documents/posts/${encodeURIComponent(postId)}` +
    `?key=${apiKey}`;

  let response;
  try {
    response = await fetch(endpoint);
  } catch (_) {
    return null;
  }
  if (!response.ok) return null;

  const doc = await response.json();
  if (!doc || !doc.fields) return null;

  const f = doc.fields;
  return {
    title: readString(f.title),
    body: readString(f.body),
    authorName: readString(f.authorName) || "Unknown",
    imageUrl: readString(f.imageUrl),
    videoUrl: readString(f.videoUrl),
    previewUrl: readString(f.previewUrl),
    media: readMedia(f.media),
    linkUrl: readString(f.linkUrl),
    links: readStringArray(f.links),
    upvoteCount: readInt(f.upvoteCount),
    downvoteCount: readInt(f.downvoteCount),
    commentCount: readInt(f.commentCount),
  };
}

function readString(field) {
  if (!field) return "";
  if (typeof field.stringValue === "string") return field.stringValue;
  return "";
}

/** Unwraps Firestore's array-of-maps encoding for the `media` field. */
function readMedia(field) {
  const values = field && field.arrayValue && field.arrayValue.values;
  if (!Array.isArray(values)) return [];

  return values
    .map((entry) => {
      const f = entry && entry.mapValue && entry.mapValue.fields;
      if (!f) return null;
      return {
        url: readString(f.url),
        type: readString(f.type) || "image",
        thumbUrl: readString(f.thumbUrl),
      };
    })
    .filter(Boolean);
}

function readInt(field) {
  if (!field) return 0;
  if (field.integerValue != null) return Number(field.integerValue);
  if (field.doubleValue != null) return Number(field.doubleValue);
  return 0;
}

// --- Rendering ---------------------------------------------------------

function htmlResponse(html, status) {
  return new Response(html, {
    status,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "public, max-age=120",
    },
  });
}

function esc(value) {
  return String(value == null ? "" : value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/** Truncates for the social-preview description. */
function preview(text, max) {
  const clean = String(text || "").replace(/\s+/g, " ").trim();
  return clean.length <= max ? clean : clean.slice(0, max - 1) + "…";
}

function renderPost(postId, post) {
  const appLink = `kinetix://post/${encodeURIComponent(postId)}`;
  const description = preview(post.body || post.title, 180);

  // Every attachment, as a swipeable carousel when there's more than one.
  const media = renderMedia(post);

  // Every link, each on its own line. Falls back to the legacy single field
  // for posts written before the multi-link `links` array existed.
  const allLinks =
    post.links && post.links.length ? post.links : post.linkUrl ? [post.linkUrl] : [];
  const link = allLinks
    .map(
      (u) =>
        `<a class="postlink" href="${esc(u)}" rel="noopener noreferrer nofollow">${esc(u)}</a>`,
    )
    .join("");

  // The rich card: image, title underneath, app name as the source.
  //
  // previewUrl is a 1200x630 still the app renders at upload time. Using it
  // rather than the raw imageUrl is what keeps the card compact — chat apps
  // size the card to the image's aspect ratio, so a portrait screenshot used
  // directly produces an enormous card. It's also the only preview a video
  // post has, since there's no imageUrl to fall back on.
  //
  // Declaring the dimensions lets scrapers lay the card out before they've
  // finished fetching the image, and stops them guessing at a taller one.
  const previewImage = throughWorker(post.previewUrl || post.imageUrl || "");
  const hasExactSize = Boolean(post.previewUrl);

  const imageTags = previewImage
    ? `
    <meta property="og:image" content="${esc(previewImage)}" />
    <meta property="og:image:alt" content="${esc(post.title)}" />${
      hasExactSize
        ? `
    <meta property="og:image:width" content="1200" />
    <meta property="og:image:height" content="630" />`
        : ""
    }
    <meta name="twitter:image" content="${esc(previewImage)}" />
    <meta name="twitter:card" content="summary_large_image" />`
    : `
    <meta name="twitter:card" content="summary" />`;

  return page({
    title: `${post.title} — ${APP_NAME}`,
    head: `
    <meta property="og:type" content="article" />
    <meta property="og:site_name" content="${APP_NAME}" />
    <meta property="og:url" content="https://${SHARE_HOST}/p/${encodeURIComponent(postId)}" />
    <meta property="og:title" content="${esc(post.title)}" />
    <meta property="og:description" content="${esc(description)}" />
    ${imageTags}
    <meta name="description" content="${esc(description)}" />`,
    body: `
    <header class="bar">
      <button class="close" id="closeBtn" aria-label="Close">&#10005;</button>
      <span class="brand">${APP_NAME}</span>
      <a class="open" href="${esc(appLink)}">Open in app</a>
    </header>

    <main class="post">
      <div class="author">
        <span class="avatar">${esc((post.authorName[0] || "?").toUpperCase())}</span>
        <span class="name">${esc(post.authorName)}</span>
      </div>

      <h1>${esc(post.title)}</h1>
      ${post.body ? `<p class="body">${esc(post.body)}</p>` : ""}
      ${link}
      ${media}

      <div class="votes">
        <span class="vote" role="button" tabindex="0" aria-label="Upvote">
          &#9650; ${post.upvoteCount}
        </span>
        <span class="vote" role="button" tabindex="0" aria-label="Downvote">
          &#9660; ${post.downvoteCount}
        </span>
        <span class="cmt">&#128172; ${post.commentCount}</span>
      </div>

      <p class="cta">
        Get the ${APP_NAME} app to upvote, comment, and see the full discussion.
      </p>
    </main>`,
  });
}

// --- The community page ------------------------------------------------

async function communityPageResponse(communityId, env) {
  const community = await fetchCommunity(communityId, env);

  if (!community) {
    return htmlResponse(renderMissingCommunity(), 404);
  }

  // The community's own posts, ranked by score the same way the app ranks a
  // community feed. A failure here just renders the page with no posts.
  const posts = await fetchCommunityPosts(communityId, env);

  return htmlResponse(renderCommunity(communityId, community, posts), 200);
}

/**
 * Every post in one community, newest-scoring first.
 *
 * Uses Firestore's `:runQuery` REST endpoint with an equality filter on
 * communityId — equality alone needs no composite index — then ranks by score
 * in JS, exactly as the app's community feed does.
 */
async function fetchCommunityPosts(communityId, env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  const apiKey = env.FIREBASE_API_KEY;
  if (!projectId || !apiKey) return [];

  const endpoint =
    `https://firestore.googleapis.com/v1/projects/${projectId}` +
    `/databases/(default)/documents:runQuery?key=${apiKey}`;

  const query = {
    structuredQuery: {
      from: [{ collectionId: "posts" }],
      where: {
        fieldFilter: {
          field: { fieldPath: "communityId" },
          op: "EQUAL",
          value: { stringValue: communityId },
        },
      },
      limit: 100,
    },
  };

  let response;
  try {
    response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(query),
    });
  } catch (_) {
    return [];
  }
  if (!response.ok) return [];

  let rows;
  try {
    rows = await response.json();
  } catch (_) {
    return [];
  }
  if (!Array.isArray(rows)) return [];

  const posts = [];
  for (const row of rows) {
    const doc = row && row.document;
    if (!doc || !doc.fields) continue;
    const f = doc.fields;
    posts.push({
      id: doc.name ? doc.name.split("/").pop() : "",
      title: readString(f.title),
      body: readString(f.body),
      authorName: readString(f.authorName) || "Unknown",
      imageUrl: readString(f.imageUrl),
      videoUrl: readString(f.videoUrl),
      previewUrl: readString(f.previewUrl),
      media: readMedia(f.media),
      upvoteCount: readInt(f.upvoteCount),
      downvoteCount: readInt(f.downvoteCount),
      commentCount: readInt(f.commentCount),
      score: readInt(f.score),
    });
  }

  posts.sort((a, b) => b.score - a.score);
  return posts;
}

/**
 * Every attachment on a post, in order — mirrors the app's Post.mediaItems.
 * New posts carry a `media` array; older ones only have the legacy single
 * image/video fields, which are adapted here so both render the same way.
 */
function mediaItemsOf(post) {
  if (Array.isArray(post.media) && post.media.length) return post.media;
  if (post.videoUrl) return [{ url: post.videoUrl, type: "video", thumbUrl: post.previewUrl || "" }];
  if (post.imageUrl) return [{ url: post.imageUrl, type: "image", thumbUrl: "" }];
  return [];
}

/**
 * One attachment. Video points at the full file with the still as its poster —
 * a video's thumbUrl is only an image, so it can't be the <video> source.
 */
function renderMediaItem(item) {
  if ((item.type || "image") === "video") {
    const poster = item.thumbUrl || "";
    return `<video class="media" src="${esc(throughWorker(item.url))}" ${
      poster ? `poster="${esc(throughWorker(poster))}"` : ""
    } controls playsinline preload="metadata"></video>`;
  }
  return `<img class="media" src="${esc(throughWorker(item.url))}" alt="" loading="lazy" />`;
}

/**
 * A post's media. One attachment renders on its own; several become a swipeable
 * carousel with a counter and dots, the same as the app's post card.
 */
function renderMedia(post) {
  const items = mediaItemsOf(post);
  if (!items.length) return "";
  if (items.length === 1) return renderMediaItem(items[0]);

  const slides = items
    .map((it) => `<div class="slide">${renderMediaItem(it)}</div>`)
    .join("");
  const dots = items
    .map((_, i) => `<span class="dot${i === 0 ? " on" : ""}"></span>`)
    .join("");

  return `
    <div class="carousel">
      <div class="track">${slides}</div>
      <div class="counter"><span class="cur">1</span> / ${items.length}</div>
      <div class="dots">${dots}</div>
    </div>`;
}

/** One post as a card in the community page's feed. Read-only, no navigation. */
function renderCommunityPost(post) {
  return `
    <div class="pcard">
      <div class="author">
        <span class="avatar">${esc((post.authorName[0] || "?").toUpperCase())}</span>
        <span class="name">${esc(post.authorName)}</span>
      </div>
      <h2>${esc(post.title)}</h2>
      ${post.body ? `<p class="body">${esc(post.body)}</p>` : ""}
      ${renderMedia(post)}
      <div class="votes">
        <span class="vote" role="button" tabindex="0" aria-label="Upvote">
          &#9650; ${post.upvoteCount}
        </span>
        <span class="vote" role="button" tabindex="0" aria-label="Downvote">
          &#9660; ${post.downvoteCount}
        </span>
        <span class="cmt">&#128172; ${post.commentCount}</span>
      </div>
    </div>`;
}

/** Reads communities/{communityId} through the Firestore REST API. */
async function fetchCommunity(communityId, env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  const apiKey = env.FIREBASE_API_KEY;
  if (!projectId || !apiKey) return null;

  const endpoint =
    `https://firestore.googleapis.com/v1/projects/${projectId}` +
    `/databases/(default)/documents/communities/${encodeURIComponent(communityId)}` +
    `?key=${apiKey}`;

  let response;
  try {
    response = await fetch(endpoint);
  } catch (_) {
    return null;
  }
  if (!response.ok) return null;

  const doc = await response.json();
  if (!doc || !doc.fields) return null;

  const f = doc.fields;
  return {
    name: readString(f.name) || "Community",
    description: readString(f.description),
    categories: readStringArray(f.categories),
    creatorName: readString(f.creatorName) || "Unknown",
    memberCount: readInt(f.memberCount),
    avatarUrl: readString(f.avatarUrl),
    bannerUrl: readString(f.bannerUrl),
  };
}

/** Unwraps Firestore's array-of-strings encoding for the `categories` field. */
function readStringArray(field) {
  const values = field && field.arrayValue && field.arrayValue.values;
  if (!Array.isArray(values)) return [];
  return values.map((v) => readString(v)).filter(Boolean);
}

function renderCommunity(communityId, community, posts) {
  const appLink = `kinetix://community/${encodeURIComponent(communityId)}`;
  const description = preview(community.description || community.name, 180);
  const memberLabel = `${community.memberCount} ${
    community.memberCount === 1 ? "member" : "members"
  }`;

  const chips = community.categories.length
    ? `<div class="chips">${community.categories
        .map((c) => `<span class="chip">${esc(c)}</span>`)
        .join("")}</div>`
    : "";

  const feed = (posts && posts.length)
    ? `<section class="feed">${posts.map(renderCommunityPost).join("")}</section>`
    : `<p class="empty">No posts in this community yet.</p>`;

  const banner = community.bannerUrl
    ? `<div class="cbanner"><img src="${esc(throughWorker(community.bannerUrl))}" alt="" /></div>`
    : "";

  const avatar = community.avatarUrl
    ? `<img class="cavatar" src="${esc(throughWorker(community.avatarUrl))}" alt="" />`
    : `<span class="avatar">${esc((community.name[0] || "?").toUpperCase())}</span>`;

  const ogImage = community.bannerUrl || community.avatarUrl;

  return page({
    title: `${community.name} — ${APP_NAME}`,
    head: `
    <meta property="og:type" content="website" />
    <meta property="og:site_name" content="${APP_NAME}" />
    <meta property="og:url" content="https://${SHARE_HOST}/c/${encodeURIComponent(communityId)}" />
    <meta property="og:title" content="${esc(community.name)}" />
    <meta property="og:description" content="${esc(description)}" />
    ${ogImage ? `<meta property="og:image" content="${esc(throughWorker(ogImage))}" />` : ""}
    <meta name="twitter:card" content="${community.bannerUrl ? "summary_large_image" : "summary"}" />
    <meta name="description" content="${esc(description)}" />`,
    body: `
    <header class="bar">
      <button class="close" id="closeBtn" aria-label="Close">&#10005;</button>
      <span class="brand">${APP_NAME}</span>
      <a class="open" href="${esc(appLink)}">Open in app</a>
    </header>

    ${banner}
    <main class="post">
      <div class="author">
        ${avatar}
        <span class="name">${esc(community.name)}</span>
        <span class="joinbtn" role="button" tabindex="0" aria-label="Join">Join</span>
      </div>

      <p class="counts"><span>${esc(memberLabel)}</span></p>
      ${community.description ? `<p class="body">${esc(community.description)}</p>` : ""}
      ${chips}

      <p class="cta">
        Get the ${APP_NAME} app to join this community, post, and see the full discussion.
      </p>

      ${feed}
    </main>`,
  });
}

function renderMissingCommunity() {
  return page({
    title: `Community unavailable — ${APP_NAME}`,
    head: '<meta name="robots" content="noindex" />',
    body: `
    <header class="bar">
      <button class="close" id="closeBtn" aria-label="Close">&#10005;</button>
      <span class="brand">${APP_NAME}</span>
      <span></span>
    </header>
    <main class="post">
      <h1>Community unavailable</h1>
      <p class="body">This community may have been deleted, or the link is incorrect.</p>
    </main>`,
  });
}

// --- The profile page --------------------------------------------------

async function userPageResponse(userId, env) {
  const user = await fetchUser(userId, env);

  if (!user) {
    return htmlResponse(renderMissingUser(), 404);
  }

  const posts = await fetchUserPosts(userId, env);

  return htmlResponse(renderUser(userId, user, posts), 200);
}

/** Reads users/{userId} through the Firestore REST API. */
async function fetchUser(userId, env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  const apiKey = env.FIREBASE_API_KEY;
  if (!projectId || !apiKey) return null;

  const endpoint =
    `https://firestore.googleapis.com/v1/projects/${projectId}` +
    `/databases/(default)/documents/users/${encodeURIComponent(userId)}` +
    `?key=${apiKey}`;

  let response;
  try {
    response = await fetch(endpoint);
  } catch (_) {
    return null;
  }
  if (!response.ok) return null;

  const doc = await response.json();
  if (!doc || !doc.fields) return null;

  const f = doc.fields;
  return {
    displayName: readString(f.displayName) || "Anonymous",
    avatarUrl: readString(f.avatarUrl),
    followerCount: readInt(f.followerCount),
    followingCount: readInt(f.followingCount),
  };
}

/** Every post by one author, newest first. */
async function fetchUserPosts(userId, env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  const apiKey = env.FIREBASE_API_KEY;
  if (!projectId || !apiKey) return [];

  const endpoint =
    `https://firestore.googleapis.com/v1/projects/${projectId}` +
    `/databases/(default)/documents:runQuery?key=${apiKey}`;

  const query = {
    structuredQuery: {
      from: [{ collectionId: "posts" }],
      where: {
        fieldFilter: {
          field: { fieldPath: "authorId" },
          op: "EQUAL",
          value: { stringValue: userId },
        },
      },
      limit: 100,
    },
  };

  let response;
  try {
    response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(query),
    });
  } catch (_) {
    return [];
  }
  if (!response.ok) return [];

  let rows;
  try {
    rows = await response.json();
  } catch (_) {
    return [];
  }
  if (!Array.isArray(rows)) return [];

  const posts = [];
  for (const row of rows) {
    const doc = row && row.document;
    if (!doc || !doc.fields) continue;
    const f = doc.fields;
    posts.push({
      id: doc.name ? doc.name.split("/").pop() : "",
      title: readString(f.title),
      body: readString(f.body),
      authorName: readString(f.authorName) || "Unknown",
      imageUrl: readString(f.imageUrl),
      videoUrl: readString(f.videoUrl),
      previewUrl: readString(f.previewUrl),
      media: readMedia(f.media),
      upvoteCount: readInt(f.upvoteCount),
      downvoteCount: readInt(f.downvoteCount),
      commentCount: readInt(f.commentCount),
      createdAtSeconds: readTimestampSeconds(f.createdAt),
    });
  }

  // Newest first — a profile reads as a timeline, not a leaderboard.
  posts.sort((a, b) => b.createdAtSeconds - a.createdAtSeconds);
  return posts;
}

/** Firestore timestampValue -> epoch seconds, for sorting. */
function readTimestampSeconds(field) {
  const t = field && field.timestampValue;
  if (!t) return 0;
  const ms = Date.parse(t);
  return Number.isNaN(ms) ? 0 : Math.floor(ms / 1000);
}

function renderUser(userId, user, posts) {
  const appLink = `kinetix://user/${encodeURIComponent(userId)}`;
  const shareUrl = `https://${SHARE_HOST}/u/${encodeURIComponent(userId)}`;
  const description = `${user.displayName} on ${APP_NAME} — ${user.followerCount} ${
    user.followerCount === 1 ? "follower" : "followers"
  }`;

  const avatar = user.avatarUrl
    ? `<img class="pavatar" src="${esc(throughWorker(user.avatarUrl))}" alt="" />`
    : `<span class="pavatar pavatar-fallback">${esc(
        (user.displayName[0] || "?").toUpperCase(),
      )}</span>`;

  const feed = (posts && posts.length)
    ? `<section class="feed">${posts.map(renderCommunityPost).join("")}</section>`
    : `<p class="empty">No posts yet.</p>`;

  return page({
    title: `${user.displayName} — ${APP_NAME}`,
    head: `
    <meta property="og:type" content="profile" />
    <meta property="og:site_name" content="${APP_NAME}" />
    <meta property="og:url" content="${esc(shareUrl)}" />
    <meta property="og:title" content="${esc(user.displayName)}" />
    <meta property="og:description" content="${esc(description)}" />
    ${user.avatarUrl ? `<meta property="og:image" content="${esc(throughWorker(user.avatarUrl))}" />` : ""}
    <meta name="twitter:card" content="summary" />
    <meta name="description" content="${esc(description)}" />`,
    body: `
    <header class="bar">
      <button class="close" id="closeBtn" aria-label="Close">&#10005;</button>
      <span class="brand">${APP_NAME}</span>
      <a class="open" href="${esc(appLink)}">Open in app</a>
    </header>

    <main class="post">
      <div class="phead">
        ${avatar}
        <div class="pmeta">
          <span class="pname">${esc(user.displayName)}</span>
          <span class="csub">${user.followerCount} ${
            user.followerCount === 1 ? "follower" : "followers"
          }</span>
        </div>
        <span class="joinbtn" role="button" tabindex="0" aria-label="Follow">Follow</span>
      </div>

      ${feed}
    </main>`,
  });
}

function renderMissingUser() {
  return page({
    title: `Profile unavailable — ${APP_NAME}`,
    head: '<meta name="robots" content="noindex" />',
    body: `
    <header class="bar">
      <button class="close" id="closeBtn" aria-label="Close">&#10005;</button>
      <span class="brand">${APP_NAME}</span>
      <span></span>
    </header>
    <main class="post">
      <h1>Profile unavailable</h1>
      <p class="body">This account may not exist, or the link is incorrect.</p>
    </main>`,
  });
}

function renderMissing() {
  return page({
    title: `Post unavailable — ${APP_NAME}`,
    head: '<meta name="robots" content="noindex" />',
    body: `
    <header class="bar">
      <button class="close" id="closeBtn" aria-label="Close">&#10005;</button>
      <span class="brand">${APP_NAME}</span>
      <span></span>
    </header>
    <main class="post">
      <h1>Post unavailable</h1>
      <p class="body">This post may have been deleted, or the link is incorrect.</p>
    </main>`,
  });
}

/**
 * Shared shell. Light and dark are both styled via prefers-color-scheme so
 * the page matches the reader's system theme, the same rule the app screens
 * follow.
 */
function page({ title, head, body }) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${esc(title)}</title>
${head}
<style>
  :root {
    --bg: #ffffff;
    --surface: #f6f7fb;
    --text: #12132a;
    --muted: #6b6d85;
    --outline: #e2e3ee;
    --accent: #4a4ae0;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #0e0f1e;
      --surface: #191a2e;
      --text: #ffffff;
      --muted: #9a9cb8;
      --outline: #35375f;
      --accent: #8b8bf5;
    }
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    background: var(--bg);
    color: var(--text);
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
    line-height: 1.5;
  }
  .bar {
    position: sticky; top: 0; z-index: 2;
    display: flex; align-items: center; justify-content: space-between; gap: 12px;
    padding: 12px 16px;
    background: var(--bg);
    border-bottom: 1px solid var(--outline);
  }
  .close {
    background: none; border: 0; cursor: pointer;
    color: var(--text); font-size: 20px; line-height: 1;
    padding: 4px 8px; border-radius: 50%;
  }
  .close:hover { background: var(--surface); }
  .brand { font-weight: 700; }
  .open {
    background: var(--accent); color: #fff; text-decoration: none;
    padding: 8px 16px; border-radius: 999px; font-weight: 600; font-size: 14px;
  }
  .post { max-width: 680px; margin: 0 auto; padding: 20px 16px 56px; }
  .author { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
  .avatar {
    width: 32px; height: 32px; border-radius: 50%;
    background: var(--surface); border: 1px solid var(--outline);
    display: grid; place-items: center; font-weight: 700; color: var(--accent);
  }
  .name { font-weight: 600; }
  h1 { font-size: 22px; margin: 0 0 12px; }
  .body { white-space: pre-wrap; margin: 0 0 16px; }
  .postlink { display: block; color: var(--accent); margin-bottom: 16px; word-break: break-all; }
  .media {
    display: block; width: 100%; max-height: 70vh;
    object-fit: contain; background: #000;
    border-radius: 12px; border: 1px solid var(--outline);
  }
  .carousel { position: relative; }
  .track {
    display: flex; overflow-x: auto; scroll-snap-type: x mandatory;
    border-radius: 12px; border: 1px solid var(--outline); background: #000;
    -webkit-overflow-scrolling: touch; scrollbar-width: none;
  }
  .track::-webkit-scrollbar { display: none; }
  .slide { flex: 0 0 100%; scroll-snap-align: center; display: flex; }
  .slide .media { border: 0; border-radius: 0; }
  .counter {
    position: absolute; top: 10px; right: 10px;
    background: rgba(0,0,0,.62); color: #fff; font-size: 12px; font-weight: 600;
    padding: 3px 10px; border-radius: 999px; pointer-events: none;
  }
  .dots { display: flex; justify-content: center; gap: 6px; margin-top: 10px; }
  .dot { width: 7px; height: 7px; border-radius: 50%; background: var(--outline); transition: background .15s; }
  .dot.on { background: var(--accent); }
  .counts {
    display: flex; gap: 16px; margin-top: 16px;
    color: var(--muted); font-size: 14px;
  }
  .cbanner { width: 100%; max-height: 200px; overflow: hidden; }
  .cbanner img { width: 100%; height: 100%; max-height: 200px; object-fit: cover; display: block; }
  .cavatar {
    width: 40px; height: 40px; border-radius: 50%; object-fit: cover;
    border: 1px solid var(--outline);
  }
  .chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
  .chip {
    border: 1px solid var(--outline); border-radius: 999px;
    padding: 6px 14px; font-size: 13px; color: var(--text);
  }
  .feed { margin-top: 8px; border-top: 1px solid var(--outline); }
  .pcard {
    color: inherit;
    padding: 20px 0; border-bottom: 1px solid var(--outline);
  }
  .pcard h2 { font-size: 18px; margin: 4px 0 8px; }
  .empty { color: var(--muted); margin-top: 24px; }
  .votes { display: flex; gap: 10px; margin-top: 14px; align-items: center; }
  .vote {
    display: inline-flex; align-items: center; gap: 6px;
    border: 1px solid var(--outline); border-radius: 999px;
    padding: 6px 14px; font-size: 14px; color: var(--text);
    cursor: pointer; user-select: none;
  }
  .vote:hover { background: var(--bg); border-color: var(--accent); }
  .cmt {
    display: inline-flex; align-items: center; gap: 6px;
    padding: 6px 8px; font-size: 14px; color: var(--muted);
  }
  .joinbtn {
    margin-left: auto; cursor: pointer; user-select: none;
    background: var(--accent); color: #fff; font-weight: 600; font-size: 14px;
    padding: 8px 20px; border-radius: 999px;
  }
  .joinbtn:hover { filter: brightness(1.08); }
  .phead { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; }
  .pavatar {
    width: 64px; height: 64px; border-radius: 50%; object-fit: cover;
    border: 1px solid var(--outline); flex-shrink: 0;
  }
  .pavatar-fallback {
    display: grid; place-items: center;
    background: var(--surface); color: var(--accent);
    font-weight: 700; font-size: 24px;
  }
  .pmeta { display: flex; flex-direction: column; }
  .pname { font-weight: 700; font-size: 20px; }
  .csub { color: var(--muted); font-size: 14px; }
  .toast {
    position: fixed; left: 50%; bottom: 28px; transform: translateX(-50%) translateY(20px);
    background: var(--accent); color: #fff; font-weight: 600; font-size: 14px;
    padding: 12px 20px; border-radius: 999px; box-shadow: 0 6px 24px rgba(0,0,0,.25);
    opacity: 0; pointer-events: none; transition: opacity .2s, transform .2s; z-index: 10;
  }
  .toast.show { opacity: 1; transform: translateX(-50%) translateY(0); }
  .cta {
    margin-top: 28px; padding-top: 16px;
    border-top: 1px solid var(--outline);
    color: var(--muted); font-size: 14px;
  }
</style>
</head>
<body>
${body}
<div class="toast" id="toast"></div>
<script>
  // Close behaves the way it does on other social platforms: go back if
  // there's somewhere to go, otherwise try to close the tab outright.
  document.getElementById('closeBtn').addEventListener('click', function () {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      window.close();
    }
  });

  // A small toast, used for the vote buttons on the community feed.
  var toastTimer;
  function showToast(message) {
    var el = document.getElementById('toast');
    if (!el) return;
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('show'); }, 2200);
  }

  // Voting and joining both need an account, so on the web these buttons just
  // prompt to open the app. preventDefault stops the surrounding card link (for
  // the vote buttons) from also navigating.
  document.querySelectorAll('.vote, .joinbtn').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      showToast('Login to the app');
    });
  });

  // Multi-attachment carousels: keep the "N / M" counter and dots in step with
  // whichever slide is snapped into view as the user swipes.
  document.querySelectorAll('.carousel').forEach(function (c) {
    var track = c.querySelector('.track');
    var cur = c.querySelector('.cur');
    var dots = c.querySelectorAll('.dot');
    if (!track) return;
    track.addEventListener('scroll', function () {
      var i = Math.round(track.scrollLeft / track.clientWidth);
      if (cur) cur.textContent = String(i + 1);
      dots.forEach(function (d, di) { d.classList.toggle('on', di === i); });
    }, { passive: true });
  });
</script>
</body>
</html>`;
}
