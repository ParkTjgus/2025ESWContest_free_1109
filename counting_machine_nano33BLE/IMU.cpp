#include <Arduino.h>
#include <math.h>
#include "counting_machine.h"

#ifndef RETURN_SENSOR_FRAME     // 보드좌표계 → 센서(칩)좌표계로 역매핑할지 선택
#define RETURN_SENSOR_FRAME 1   // 1: 센서원축(chip frame)로 되돌려 반환, 0: 보드좌표계 그대로
#endif

//
// undoNano33BLEMap: board/ x=-y_s, y=-x_s, z= z_s  → sensor/ x_s=-y_b, y_s=-x_b, z_s=z_b
//
static inline void undoNano33BLEMapAccel(float& x, float& y, float& z) {
  float xb = x, yb = y; (void)z;
  x = -yb;
  y = -xb;
  // z unchanged
}

static inline void undoNano33BLEMapGyro(float& x, float& y, float& z) {
  float xb = x, yb = y; (void)z;
  x = -yb;
  y = -xb;
  // z unchanged
}

//
// IMU_getMotion6: 가속도 3축, 자이로 3축 데이터 획득
//
float ax, ay, az, gx, gy, gz;

bool IMU_getMotion6(float& outAx, float& outAy, float& outAz, 
                    float& outGx, float& outGy, float& outGz) {                   
  if (IMU.accelerationAvailable() && IMU.gyroscopeAvailable()) {
    IMU.readAcceleration(outAx, outAy, outAz);
    IMU.readGyroscope(outGx, outGy, outGz);

    #if RETURN_SENSOR_FRAME
      #ifdef TARGET_ARDUINO_NANO33BLE
        undoNano33BLEMapAccel(outAx, outAy, outAz);
        undoNano33BLEMapGyro (outGx, outGy, outGz);
      #endif
    #endif

    return isfinite(outAx) && isfinite(outAy) && isfinite(outAz) &&
           isfinite(outGx) && isfinite(outGy) && isfinite(outGz);
  } 
  return false;
}

bool IMU_getMotion6() {
  return IMU_getMotion6(ax, ay, az, gx, gy, gz);
}



//
// updateBaseline: 지정한 샘플 수(N)로부터 기준 오프셋을 계산
//
float axOff, ayOff, azOff, gxOff, gyOff, gzOff;

void updateBaseline(int N) {
  float axSum, aySum, azSum, gxSum, gySum, gzSum;
  axSum = aySum = azSum = gxSum = gySum = gzSum = 0.0f;
  int collected = 0;

  while (collected < N) {
    if (IMU_getMotion6()) {
      axSum += ax; aySum += ay; azSum += (az - 1.0f); 
      gxSum += gx; gySum += gy; gzSum += gz;
      collected++;
    }
    delayMicroseconds(100);
  }
  const float invN = 1.0f / N;
  axOff = axSum*invN; ayOff = aySum*invN; azOff = azSum*invN; 
  gxOff = gxSum*invN; gyOff = gySum*invN; gzOff = gzSum*invN;
  
  // Serial.print("  axOff = "); Serial.print(axOff, 5);
  // Serial.print("  ayOff = "); Serial.print(ayOff, 5);
  // Serial.print("  azOff = "); Serial.println(azOff, 5);
  // Serial.print("  gxOff = "); Serial.print(gxOff, 5);
  // Serial.print("  gyOff = "); Serial.print(gyOff, 5);
  // Serial.print("  gzOff = "); Serial.println(gzOff, 5);
}



//
// vectorMagnitude3D: 3D 벡터의 크기/크기제곱을 반환
//
static inline float vectorMagnitudeSq3D(float x, float y, float z) {
  return x*x + y*y + z*z;
}

static inline float vectorMagnitude3D(float x, float y, float z) {
  return sqrtf(vectorMagnitudeSq3D(x, y, z));
}



//
// readSensorData: raw 데이터를 읽어 Z축 가속도를 계산
//
//float axG, ayG, azG, gxR, gyR, gzR;
float SVM, AngleX;

void readSensorData(float dt) {
  if (!IMU_getMotion6()) return;

  float axG, ayG, azG, gxR, gyR, gzR;
  axG = (ax - axOff); ayG = (ay - ayOff); azG = (az - azOff);
  gxR = (gx - gxOff); gyR = (gy - gyOff); gzR = (gz - gzOff);

  // 횟수 측정용
  SVM = (vectorMagnitude3D(axG, ayG, azG) - 1.0f) * 981.0f; //cm단위

  // 기울기 측정용
  if (vectorMagnitudeSq3D(axG, ayG, azG) > 1e-9f) {
    float inv = 1.0f / vectorMagnitude3D(axG, ayG, azG);
    axG *= inv; ayG *= inv; azG *= inv; // 단위벡터 정규화

    float ac_AngleX = atan2f(ayG,azG) * RAD_TO_DEG;

    const float ALPHA = 0.988f;
    AngleX = ALPHA * (AngleX + gxR * dt) + (1.0 - ALPHA) * ac_AngleX;
  } else {
    AngleX = AngleX + gxR * dt;
  }
}



// Serial.print(axG, 5); Serial.print("      ");
// Serial.print(ayG, 5); Serial.print("      ");
// Serial.print(azG, 5); Serial.print("      ");
// Serial.print(gxR, 5); Serial.print("      ");
// Serial.print(gyR, 5); Serial.print("      ");
// Serial.print(gzR, 5); Serial.print("      ");
// Serial.println();

// Serial.print("preNorm a = ");
// Serial.print(axG, 6); Serial.print(", ");
// Serial.print(ayG, 6); Serial.print(", ");
// Serial.print(azG, 6); Serial.print("  |a|=");
// Serial.println(vectorMagnitude3D(axG, ayG, azG), 6);