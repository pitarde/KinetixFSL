// ============================================================
// kinetix-upload — Cloudflare Worker
//
// Two jobs:
//   POST /                      multipart upload from the Android app → R2
//   GET  /p/{postId}            public web page for a shared post
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

/**
 * Answers the share routes. Returns null for every other request so the
 * upload handler above can take it.
 */
async function handleShareRoutes(request, env) {
  const url = new URL(request.url);

  if (url.pathname === "/.well-known/assetlinks.json") {
    return assetLinksResponse(env);
  }

  if (url.pathname.startsWith("/p/")) {
    const postId = url.pathname.slice("/p/".length).split("/")[0];
    if (!postId) return null;
    return postPageResponse(postId, env);
  }

  return null;
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
    linkUrl: readString(f.linkUrl),
    upvoteCount: readInt(f.upvoteCount),
    commentCount: readInt(f.commentCount),
  };
}

function readString(field) {
  if (!field) return "";
  if (typeof field.stringValue === "string") return field.stringValue;
  return "";
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

  const media = post.videoUrl
    ? `<video class="media" src="${esc(post.videoUrl)}" ${
        post.previewUrl ? `poster="${esc(post.previewUrl)}"` : ""
      } controls playsinline></video>`
    : post.imageUrl
      ? `<img class="media" src="${esc(post.imageUrl)}" alt="" />`
      : "";

  const link = post.linkUrl
    ? `<a class="postlink" href="${esc(post.linkUrl)}" rel="noopener noreferrer nofollow">${esc(post.linkUrl)}</a>`
    : "";

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
  const previewImage = post.previewUrl || post.imageUrl || "";
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

      <div class="counts">
        <span>${post.upvoteCount} upvotes</span>
        <span>${post.commentCount} comments</span>
      </div>

      <p class="cta">
        Get the ${APP_NAME} app to upvote, comment, and see the full discussion.
      </p>
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
  .counts {
    display: flex; gap: 16px; margin-top: 16px;
    color: var(--muted); font-size: 14px;
  }
  .cta {
    margin-top: 28px; padding-top: 16px;
    border-top: 1px solid var(--outline);
    color: var(--muted); font-size: 14px;
  }
</style>
</head>
<body>
${body}
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
</script>
</body>
</html>`;
}
