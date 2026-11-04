package com.bytegrep.oplist_manager.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bytegrep.oplist_manager.R

/** 应用 Logo（登录页、关于页） */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 88.dp) {
    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = "OpenList Manager Logo",
        modifier = modifier.size(size),
    )
}
