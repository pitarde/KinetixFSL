package com.example.kinetixfsl.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
<<<<<<< HEAD
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
=======
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.R
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private const val HOLD_MILLIS = 900L

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val markAlpha = remember { Animatable(0f) }
    val markScale = remember { Animatable(0.86f) }
    val wordmarkAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { markAlpha.animateTo(1f, tween(500, easing = LinearOutSlowInEasing)) }
        markScale.animateTo(1f, tween(650, easing = FastOutSlowInEasing))

        wordmarkAlpha.animateTo(1f, tween(400, easing = LinearOutSlowInEasing))

        delay(timeMillis = HOLD_MILLIS)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
<<<<<<< HEAD
            .background(MaterialTheme.colorScheme.background),
=======
            .background(Color.White),
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.splash1_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(250.dp)
                    .scale(markScale.value)
                    .alpha(markAlpha.value),
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SplashScreenPreview() {
    KinetixFSLTheme {
        SplashScreen(onFinished = {})
    }
}