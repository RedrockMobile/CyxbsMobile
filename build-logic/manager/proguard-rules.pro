#####################################################################
#
#   混淆文件因为模块下没有导包，所以会报错，但不影响
#
#####################################################################
#noinspection ShrinkerUnresolvedReference
#-------------------------------------------定制化区域----------------------------------------------
#---------------------------------1.实体类---------------------------------
#原因：实体类数据，一定不能混淆，忽略了Serializable接口和Parcelable接口,实体类必须实现这两个接口
-keepclassmembers class * implements java.io.Serializable { <fields>; }
-keepclassmembers class * implements android.os.Parcelable { <fields>; }
-keepclassmembers class * implements com.cyxbs.components.config.serializable.GsonDataBean { <fields>; }
#-------------------------------------------------------------------------

#---------------------------------2.第三方包-------------------------------
##
## 注意：
## 1、混淆文档必须以官方为准，请不要随便找篇博客就抄下来
## 2、请一定要标注好 lib 的名字，和它的项目地址
## 3、有些 lib 的 README 中没有写混淆规则，单可能它写在了项目文件里，请仔细搜索下名字带有 proguard 的文件
##

## EventBus https://github.com/greenrobot/EventBus
-keepattributes *Annotation*
-keepclassmembers class * {
    #noinspection ShrinkerUnresolvedReference
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclassmembers class org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}
-keep class org.greenrobot.eventbus.android.AndroidComponentsImpl

# ===============

# Glide https://github.com/bumptech/glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# ===============

# Retrofit https://github.com/square/retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions.*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ===============

## ktor https://github.com/ktorio/ktor

-dontwarn org.slf4j.**
-dontwarn io.ktor.network.sockets.**
-dontwarn kotlinx.serialization.internal.**
-dontwarn org.slf4j.helpers.SubstituteLogger

-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider

# ===============

## OkHttp https://github.com/square/okhttp
-dontwarn javax.annotation.**
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# 下面这个是 OkHttp 依赖的 Okio
-dontwarn org.codehaus.mojo.animal_sniffer.*

# ===============

## Rxjava3 https://github.com/ReactiveX/RxJava
-dontwarn java.util.concurrent.Flow*

# ===============

## Gson https://github.com/google/gson/tree/master/examples/android-proguard-example
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.examples.android.model.** { <fields>; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  #noinspection ShrinkerUnresolvedReference
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ===============

## kotlinx-serialization https://github.com/Kotlin/kotlinx.serialization
## 官方的混淆规则有点问题，我改了一下
-keepclasseswithmembers @kotlinx.serialization.Serializable class ** {
    static *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclasseswithmembers class <1>$Companion {
    *** serializer();
}

# ===============

## Bugly https://bugly.qq.com/docs/
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}


# ===============

# Rx
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}
-dontwarn rx.internal.util.unsafe.**
-keep class com.tbruyelle.rxpermissions2.** { *; }
-keep interface com.tbruyelle.rxpermissions2.** { *; }
-dontwarn io.rx_cache.internal.**

#ucrop
#ucrop https://github.com/Yalantis/uCrop
-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }

#nineoldandroids
-keep class com.nineoldandroids.** { *; }


# ===============

# renderscript
-keep class android.support.v8.renderscript.** { *; }

# 高德地图
# 高德地图 https://lbs.amap.com/api/android-sdk/guide/create-project/dev-attention
# 3D 地图
-keep class com.amap.api.maps.**{*;}
-keep class com.autonavi.**{*;}
-keep class com.amap.api.trace.**{*;}

# 定位
-keep class com.amap.api.location.**{*;}
-keep class com.amap.api.fence.**{*;}
-keep class com.loc.**{*;}
-keep class com.autonavi.aps.amapapi.model.**{*;}

# 搜索
-keep class com.amap.api.services.**{*;}

# 2D地图
-keep class com.amap.api.maps2d.**{*;}
-keep class com.amap.api.mapcore2d.**{*;}

# 导航
-keep class com.amap.api.navi.**{*;}
-keep class com.autonavi.**{*;}

# 这个内部有些被 native 调用，官方也没给特定的混淆规则，所以就全部保留
-keep class com.amap.location.** { *; }

# kotlin协程
# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Most of volatile fields are updated with AFU and should not be mangled
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

#-------------------------------------------------------------------------

#---------------------------------3.与js互相调用的类------------------------



#-------------------------------------------------------------------------

#---------------------------------4.反射相关的类和方法-----------------------
##
## 注意：
## 1、请一定要写好模块位置和原因，原因请精确到某个方法
##

# 模块：module_store，原因：StoreCenterActivity 的 initRefreshLayout() 方法中
-keepclassmembernames class androidx.swiperefreshlayout.widget.SwipeRefreshLayout {
    private int mTouchSlop;
}

# 模块：module_store，原因：StoreCenterActivity 的 initTabLayout() 方法中
-keepclassmembernames class com.google.android.material.badge.BadgeDrawable {
    private final float badgeRadius;
}


#----------------------------------------------------------------------------
#---------------------------------------------------------------------------------------------------

#-------------------------------------------基本不用动区域--------------------------------------------
#---------------------------------基本指令区----------------------------------
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-printmapping proguardMapping.txt
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-keepattributes *Annotation*,InnerClasses
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
#----------------------------------------------------------------------------

#---------------------------------默认保留区---------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends android.app.Application
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep public class * extends android.support.v4.**
-keep public class * extends android.support.v7.**
-keep public class * extends android.support.annotation.**
-keep class android.support.** {*;}
-keep public class * implements java.lang.annotation.Annotation { *;}
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclasseswithmembers class * {
    #noinspection ShrinkerUnresolvedReference
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keep class * implements android.os.Parcelable {
  #noinspection ShrinkerUnresolvedReference
  public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keep class **.R$* {
 *;
}
-keepclassmembers class * {
    void *(**On*Event);
}
-ignorewarnings
#----------------------------------------------------------------------------

#---------------------------------webview------------------------------------
-keepclassmembers class * extends android.webkit.WebViewClient {
   #noinspection ShrinkerUnresolvedReference
   public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
   public boolean *(android.webkit.WebView, java.lang.String);
}
#----------------------------------------------------------------------------
#---------------------------------------------------------------------------------------------------
#---------------------------------UMeng--------------------------------------
-dontwarn com.umeng.**
-dontwarn com.taobao.**
-dontwarn anet.channel.**
-dontwarn anetwork.channel.**
-dontwarn org.android.**
-dontwarn org.apache.thrift.**
-dontwarn com.xiaomi.**
-dontwarn com.huawei.**
-dontwarn com.meizu.**

-keep class com.taobao.** {*;}
-keep class org.android.** {*;}
-keep class anet.channel.** {*;}
-keep class com.umeng.** {*;}

-keep class com.uc.** {*;}

-keepclassmembers class * {
   #noinspection ShrinkerUnresolvedReference
   public <init> (org.json.JSONObject);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.zui.** {*;}
-keep class com.miui.** {*;}
-keep class com.heytap.** {*;}
-keep class a.** {*;}
-keep class com.vivo.** {*;}

-keep public class com.mredrock.cyxbs.R$*{
public static final int *;
}
#----------------------------------------------------------------------------

#-dontshrink 这行代码去除掉可以删去不用的包
-keep,allowshrinking class com.umeng.message.* {
    public <fields>;
    public <methods>;
}
-keep,allowshrinking class org.android.agoo.impl.*{
    public <fields>;
    public <methods>;
}
-keep,allowshrinking class org.android.agoo.service.* {*;}
-keep,allowshrinking class org.android.spdy.**{*;}

