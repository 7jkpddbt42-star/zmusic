package com.musicbox.app.ui.components.liquidglass

import android.os.Build

/*
 * 液态玻璃核心着色器（AGSL）与能力判断。
 *
 * 算法改编自 Kyant0/AndroidLiquidGlass（https://github.com/Kyant0/AndroidLiquidGlass，
 * Apache License 2.0）。按本项目「零新增 Gradle 依赖」的约束，以 AGSL 字符串内联实现，
 * 运行时通过 android.graphics.RuntimeShader 编译执行（API 33+）。
 *
 * - [REFRACTION_SHADER]：圆角矩形 SDF 边缘折射。
 * - [REFRACTION_DISPERSION_SHADER]：折射基础上叠加 7 采样 RGB 色散。
 * - [HIGHLIGHT_SHADER]：沿光照方向的边缘高光。
 */
const val REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;         // 玻璃元素自身尺寸（px）
uniform float2 offset;       // 玻璃元素在背景层坐标系中的位置（px）
uniform float4 cornerRadii;  // 左上/右上/右下/左下（px）
uniform float refractionHeight;  // 边缘折射带厚度（px）
uniform float refractionAmount;  // 折射位移幅度（传负值，向内折射）
uniform float depthEffect;       // 0/1，内部深度暗化

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) { if (coord.y <= 0.0) return radii.y; else return radii.z; }
    else { if (coord.y <= 0.0) return radii.x; else return radii.w; }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord + offset);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord + offset);
}
"""

/*
 * 折射 + 色散版本：在折射位移方向上取 7 个采样点（0、±1/3、±2/3、±1），
 * R/G/B 各通道取不同侧的加权均值，模拟玻璃边缘的光谱分离。
 */
const val REFRACTION_DISPERSION_SHADER = """
uniform shader content;
uniform float2 size;         // 玻璃元素自身尺寸（px）
uniform float2 offset;       // 玻璃元素在背景层坐标系中的位置（px）
uniform float4 cornerRadii;  // 左上/右上/右下/左下（px）
uniform float refractionHeight;  // 边缘折射带厚度（px）
uniform float refractionAmount;  // 折射位移幅度（传负值，向内折射）
uniform float chromaticAberration;  // 色散强度（0 关闭）
uniform float depthEffect;       // 0/1，内部深度暗化

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) { if (coord.y <= 0.0) return radii.y; else return radii.z; }
    else { if (coord.y <= 0.0) return radii.x; else return radii.w; }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord + offset);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    float2 refractedCoord = coord + d * grad;
    float2 ca = (refractedCoord - coord) * chromaticAberration;
    half4 c0 = content.eval(refractedCoord + offset);
    half4 cp1 = content.eval(refractedCoord + offset + ca);
    half4 cp2 = content.eval(refractedCoord + offset + ca * 2.0);
    half4 cp3 = content.eval(refractedCoord + offset + ca * 3.0);
    half4 cn1 = content.eval(refractedCoord + offset - ca);
    half4 cn2 = content.eval(refractedCoord + offset - ca * 2.0);
    half4 cn3 = content.eval(refractedCoord + offset - ca * 3.0);
    half r = (c0.r + cp1.r * 2.0 + cp2.r * 2.0 + cp3.r) / 6.0;
    half g = (cp1.g + c0.g * 2.0 + cn1.g) / 4.0;
    half b = (c0.b + cn1.b * 2.0 + cn2.b * 2.0 + cn3.b) / 6.0;
    return half4(r, g, b, 1.0);
}
"""

/*
 * 边缘高光：以圆角矩形 SDF 梯度与光照方向的点积计算强度，
 * falloff 越大高光越收敛于受光边缘。
 */
const val HIGHLIGHT_SHADER = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) { if (coord.y <= 0.0) return radii.y; else return radii.z; }
    else { if (coord.y <= 0.0) return radii.x; else return radii.w; }
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    half intensity = half(pow(abs(d), falloff));
    return color * intensity;
}
"""

/** 运行时折射玻璃（AGSL RuntimeShader + RenderEffect）最低要求 API 33。 */
fun isRuntimeShaderGlassSupported(): Boolean = Build.VERSION.SDK_INT >= 33

/** RenderEffect 模糊（背景实时模糊链）最低要求 API 31。 */
fun isBackdropBlurSupported(): Boolean = Build.VERSION.SDK_INT >= 31

/**
 * 组装 AGSL cornerRadii（float4：左上/右上/右下/左下），各角半径钳制到 [maxRadius]，
 * 防止极小元素上圆角半径超过短边一半导致 SDF 退化。纯函数，便于 JVM 单测。
 */
fun cornerRadiiFloatArray(
    topLeftPx: Float,
    topRightPx: Float,
    bottomRightPx: Float,
    bottomLeftPx: Float,
    maxRadius: Float,
): FloatArray = floatArrayOf(
    topLeftPx.coerceAtMost(maxRadius),
    topRightPx.coerceAtMost(maxRadius),
    bottomRightPx.coerceAtMost(maxRadius),
    bottomLeftPx.coerceAtMost(maxRadius),
)
