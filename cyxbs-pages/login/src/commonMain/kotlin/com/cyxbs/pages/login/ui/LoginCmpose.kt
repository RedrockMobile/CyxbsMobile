package com.cyxbs.pages.login.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.appName
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.pages.login.viewmodel.LoginViewModel
import cyxbsmobile.cyxbs_pages.login.generated.resources.Res
import cyxbsmobile.cyxbs_pages.login.generated.resources.login_ic_password
import cyxbsmobile.cyxbs_pages.login.generated.resources.login_ic_username
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieClipSpec
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieAnimatable
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.seconds

/**
 * 登录界面
 *
 * @author 985892345
 * @date 2024/12/30
 */
@Composable
fun LoginPage() {
  viewModel { LoginViewModel() } // wasm 无法反射 new 对象，这里需要提供 factory
  ConstraintLayout(
    constraintSet = createConstraintSet(),
    modifier = Modifier.fillMaxSize()
      .background(LocalAppColors.current.whiteBlack)
      .systemBarsPadding(),
    animateChangesSpec = spring(
      stiffness = Spring.StiffnessMediumLow,
    ),
  ) {
    TitleCompose(modifier = Modifier.layoutId(Element.Title))
    SubTitleCompose(modifier = Modifier.layoutId(Element.SubTitle))
    StuNumPasswordCompose(modifier = Modifier.layoutId(Element.StuNumPassword))
    UserAgreementCompose(modifier = Modifier.layoutId(Element.UserAgreement))
    ForgetPasswordCompose(modifier = Modifier.layoutId(Element.ForgetPassword))
    LoginBtnCompose(modifier = Modifier.layoutId(Element.LoginBtn))
    TouristModeCompose(modifier = Modifier.layoutId(Element.TouristMode))
    LoginAnimCompose(modifier = Modifier.layoutId(Element.LoginAnim))
  }
  UserAgreementDialog()
}

@Composable
private fun createConstraintSet(): ConstraintSet {
  val viewModel = viewModel(LoginViewModel::class)
  val windowSize = getWindowScreenSize()
  return ConstraintSet {
    LoginConstraintSet(
      scope = this,
      viewModel = viewModel,
      windowSize = windowSize,
    ).createConstrain() // 所有控件的位置由该函数统一调整
  }
}

@Composable
private fun TitleCompose(modifier: Modifier = Modifier) {
  Text(
    modifier = modifier,
    text = "登录",
    fontSize = 34.sp,
    color = LocalAppColors.current.tvLv2
  )
}

@Composable
private fun SubTitleCompose(modifier: Modifier = Modifier) {
  Text(
    modifier = modifier,
    text = "您好鸭，欢迎来到$appName~",
    fontSize = 18.sp,
    color = LocalAppColors.current.tvLv2.copy(alpha = 0.6F),
  )
}

@Composable
private fun StuNumPasswordCompose(modifier: Modifier = Modifier) {
  MaterialTheme(
    typography = MaterialTheme.typography.copy( // OutlinedTextField 的 label 需要通过这个才能修改字体大小
      caption = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
      subtitle1 = MaterialTheme.typography.subtitle1.copy(fontSize = 14.sp)
    )
  ) {
    Column(modifier = modifier) {
      StuNumCompose()
      PasswordCompose()
    }
  }
}

@Composable
private fun StuNumCompose(modifier: Modifier = Modifier) {
  val viewModel = viewModel(LoginViewModel::class)
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Image(
      modifier = Modifier.padding(top = 8.dp).size(24.dp),
      painter = painterResource(Res.drawable.login_ic_username),
      contentDescription = null,
    )
    OutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      value = viewModel.stuNum.value,
      singleLine = true,
      onValueChange = {
        viewModel.stuNum.value = it
      },
      textStyle = TextStyle(fontSize = 14.sp),
      label = {
        Text(
          text = "请输入学号",
          maxLines = 1,
        )
      },
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Next,
      ),
      colors = TextFieldDefaults.outlinedTextFieldColors(
        textColor = 0xFF333333.dark(Color.White),
        focusedLabelColor = 0xFF788EFA.dark(Color.White),
        unfocusedLabelColor = Color(0xFF999999),
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        errorBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
      )
    )
  }
}

@Composable
private fun PasswordCompose(modifier: Modifier = Modifier) {
  val viewModel = viewModel(LoginViewModel::class)
  val oldText = remember { mutableStateOf("") }
  val visualTransformationAll = remember {
    PasswordVisualTransformation()
  }
  // 密码末位可见性设置
  val visualTransformationLast = remember {
    VisualTransformation {
      TransformedText(
        AnnotatedString(
          '\u2022'.toString().repeat(oldText.value.length) +
              viewModel.password.value.substringAfter(oldText.value)
        ),
        OffsetMapping.Identity
      )
    }
  }
  val visualTransformation = remember { mutableStateOf(visualTransformationLast) }
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Image(
      modifier = Modifier.padding(top = 8.dp).size(24.dp),
      painter = painterResource(Res.drawable.login_ic_password),
      contentDescription = null,
    )
    OutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      value = viewModel.password.value,
      visualTransformation = visualTransformation.value,
      onValueChange = {
        if (visualTransformation.value !== VisualTransformation.None) {
          if (it.length > viewModel.password.value.length) {
            visualTransformation.value = visualTransformationLast
            oldText.value = viewModel.password.value
          } else {
            // 删减时隐藏所有字符
            visualTransformation.value = visualTransformationAll
            oldText.value = it
          }
        }
        viewModel.password.value = it
      },
      singleLine = true,
      textStyle = TextStyle(fontSize = 14.sp),
      label = {
        Text(
          text = "默认统一认证码后六位",
          maxLines = 1,
          modifier = Modifier
        )
      },
      trailingIcon = {
        var isVisible by remember { mutableStateOf(false) }
        Icon(
          modifier = Modifier.clickableNoIndicator {
            isVisible = !isVisible
            if (isVisible) {
              visualTransformation.value = VisualTransformation.None
            } else {
              visualTransformation.value = visualTransformationAll
            }
          },
          imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
          contentDescription = null,
        )
      },
      keyboardActions = KeyboardActions {
        viewModel.clickLogin()
      },
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Go,
      ),
      colors = TextFieldDefaults.outlinedTextFieldColors(
        textColor = 0xFF333333.dark(Color.White),
        focusedLabelColor = 0xFF788EFA.dark(Color.White),
        unfocusedLabelColor = Color(0xFF999999),
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        errorBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
      )
    )
  }
  LaunchedEffect(Unit) {
    snapshotFlow { visualTransformation.value }.collectLatest {
      if (it === visualTransformationLast) {
        delay(1.seconds) // 延迟一秒后自动隐藏密码
        visualTransformation.value = visualTransformationAll
      }
    }
  }
}


@Composable
private fun UserAgreementCompose(modifier: Modifier = Modifier) {
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    UserAgreementCheckCompose()
    UserAgreementTextCompose(modifier = Modifier.padding(start = 12.dp))
  }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun UserAgreementCheckCompose(modifier: Modifier = Modifier) {
  val viewModel = viewModel(LoginViewModel::class)
  val checkLottie = rememberLottieComposition {
    LottieCompositionSpec.JsonString(
      Res.readBytes("files/lottie_check_login.json").decodeToString()
    )
  }
  val progress = rememberLottieAnimatable()
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Image(
      modifier = Modifier.size(18.dp).clickableNoIndicator {
        viewModel.isCheckUserArgument.value = !viewModel.isCheckUserArgument.value
      },
      painter = rememberLottiePainter(
        composition = checkLottie.value,
        progress = { progress.progress },
      ),
      contentDescription = "同意用户协议和隐私政策"
    )
  }
  LaunchedEffect(Unit) {
    checkLottie.await()
    snapshotFlow { viewModel.isCheckUserArgument.value }.collectLatest {
      if (it) {
        progress.animate(
          composition = checkLottie.value,
          clipSpec = LottieClipSpec.Progress(progress.progress, 1F)
        )
      } else {
        progress.animate(
          composition = checkLottie.value,
          clipSpec = LottieClipSpec.Progress(0F, 0.39F) // 0.39 为刚好画完一圈的进度
        )
      }
    }
  }
}

@Composable
private fun UserAgreementTextCompose(modifier: Modifier = Modifier) {
  val viewModel = viewModel(LoginViewModel::class)
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = "同意",
      color = Color(0xFFABBCD8),
      fontSize = 11.sp,
    )
    Text(
      text = "《用户协议》", color = Color(0xFF2CDEFF), fontSize = 11.sp,
      modifier = Modifier
        .clickable {
          viewModel.clickUserAgreement()
        }
    )
    Text(
      text = "和",
      color = Color(0xFFABBCD8),
      fontSize = 11.sp,
    )
    Text(
      text = "《隐私政策》", color = Color(0xFF2CDEFF), fontSize = 11.sp,
      modifier = Modifier.clickable {
        viewModel.clickPrivacyPolicy()
      }
    )
  }
}

@Composable
private fun ForgetPasswordCompose(modifier: Modifier = Modifier) {
  val viewModel = viewModel(LoginViewModel::class)
  Text(
    modifier = modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
      .clickable {
        viewModel.clickForgetPassword()
      },
    text = "忘记密码?",
    fontSize = 12.sp,
    color = Color(0xFFABBCD8)
  )
}

@Composable
private fun LoginBtnCompose(modifier: Modifier = Modifier) {
  val viewModel = viewModel(LoginViewModel::class)
  val isClicked = remember { mutableStateOf(false) }
  Box(
    modifier = modifier.height(52.dp),
    contentAlignment = Alignment.Center,
  ) {
    AnimatedContent(
      targetState = isClicked.value,
    ) {
      if (!it) {
        Card(
          modifier = Modifier.fillMaxSize(),
          backgroundColor = Color(0xFF4A44E4),
          shape = CircleShape,
        ) {
          Box(
            modifier = Modifier.clickable {
              viewModel.clickLogin()
            },
            contentAlignment = Alignment.Center
          ) {
            Text(text = "登 录", color = Color.White, fontSize = 18.sp)
          }
        }
      } else {
        CircularProgressIndicator()
      }
    }
  }
}

@Composable
private fun TouristModeCompose(modifier: Modifier = Modifier) {
  val viewModel = viewModel(LoginViewModel::class)
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = "没有学号么？",
      color = Color(0xFFABBCD8),
      fontSize = 13.sp,
    )
    Text(
      text = "游客模式吧",
      color = LocalAppColors.current.tvLv4,
      fontSize = 13.sp,
      modifier = Modifier.clickable {
        viewModel.clickTouristMode()
      }.padding(horizontal = 3.dp, vertical = 1.dp)
    )
  }
}

// 登录动画界面
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun LoginAnimCompose(modifier: Modifier = Modifier) {
  val viewModel = viewModel(LoginViewModel::class)
  val loginLottie = rememberLottieComposition {
    LottieCompositionSpec.JsonString(
      Res.readBytes("files/lottie_login_anim.json").decodeToString()
    )
  }
  val progress = rememberLottieAnimatable()
  Image(
    modifier = modifier.fillMaxSize(),
    painter = rememberLottiePainter(
      composition = loginLottie.value,
      progress = { progress.progress },
    ),
    contentDescription = "登录动画"
  )
  LaunchedEffect(Unit) {
    loginLottie.await()
    snapshotFlow { viewModel.isLoginAnim.value }.collectLatest {
      if (it) {
        progress.animate(
          composition = loginLottie.value,
          iterations = Compottie.IterateForever,
        )
      }
    }
  }
}
