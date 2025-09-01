#include <Arduino.h>

#include "IMU.h"
#include "counting_machine.h"


// ===== 전역 함수 =============================================================================================================



//
// processMotion: 가속도 필터링 및 적분을 통해 속도와 위치를 업데이트
//
float prevLowPassSVM = 0.0f, lowPassSVM = 0.0f;                 // 가속도 로우패스 필터링 값
float prevVelocity = 0.0f, velocity = 0.0f;                     // 적분된 속도
float prevHighPassVelocity = 0.0f, highPassVelocity = 0.0f;     // 속도 하이패스 필터링 값
float prevLowPassVelocity = 0.0f, lowPassVelocity = 0.0f;       // 속도 로우패스 필터링 값

static bool movementStopped = true;                             // 운동이 정지된 상태 표시
static uint8_t stabilityCounter = 0;                            // 운동 정지 판단용 카운터

const float ACCEL_LOW_PASS_COEFF = 53.0f;                       // 가속도 로우패스 필터 상수
const float VEL_HIGH_PASS_COEFF  =  0.5f;                       // 속도 하이패스 필터 상수
const float VEL_LOW_PASS_COEFF   = 70.0f;                       // 속도 로우패스 필터 상수

void processMotion(float dt) {
  // 가속도에 로우패스 필터 적용
  lowPassSVM = (prevLowPassSVM + dt * SVM * ACCEL_LOW_PASS_COEFF) / (1 + ACCEL_LOW_PASS_COEFF * dt);
  
  if (fabsf(lowPassSVM) <= 50) {
    // 가속도가 기준치 이하이면 속도 감쇠 처리
    lowPassVelocity = prevLowPassVelocity / 1.02f;

    // 운동 상태 진행 판단
    stabilityCounter++;
    if (stabilityCounter > 5) {
      movementStopped = true;
      stabilityCounter = 0;
    }
    // movementStopped = true;
    // stabilityCounter = 0;
  } else {
    // 가속도 적분 -> 속도 (trapezoid 적분)
    velocity = prevVelocity + ((lowPassSVM + prevLowPassSVM) / 2) * dt;
    // 속도에 하이패스 필터 적용
    highPassVelocity = (VEL_HIGH_PASS_COEFF / (VEL_HIGH_PASS_COEFF + dt)) * (prevHighPassVelocity + velocity - prevVelocity);
    
    // 드리프트 보정을 위한 보상
    highPassVelocity -= highPassVelocity / 1000.0f;
    
    // 하이패스 처리된 속도에 로우패스 필터 적용
    lowPassVelocity = (prevLowPassVelocity + dt * highPassVelocity * VEL_LOW_PASS_COEFF) / (1 + VEL_LOW_PASS_COEFF * dt);
    // 필터링된 속도 적분 -> 위치 (trapezoid 적분)
    position = prevPosition + ((lowPassVelocity + prevLowPassVelocity) / 2) * dt;

    // 운동 상태 정지 판단
    // stabilityCounter++;
    // if (stabilityCounter > 5) {
    //   movementStopped = false;
    //   stabilityCounter = 0;
    // }
    movementStopped = false;
    stabilityCounter = 0;
  }
}



//
// detectRep: 위치 변화와 변화율을 분석하여 운동 반복을 검출하고 카운트 업데이트
//
float prevPosition = 0.0f, position = 0.0f;                     // 적분된 위치
float prevDeltaPosition = 0.0f, deltaPosition = 0.0f;           // 위치 변화량
float averageRate = 5.0f, currentRate = 0.0f;                   // 위치 변화율
float lastPosition = 0.0f;                                      // 검출 기준 위치 (peak 발생 시점)
uint16_t prevRepCount = 0, repCount = 0;                        // 사이클(반동작) 카운트
uint16_t preOneRepMaxCount = 0, oneRepMaxCount = 0;             // 완전한 반복 횟수 (1RM)

static bool directionChanged = false;                           // 피크 여부 판단
static bool reachedPeak = false;                                // 피크 여부 판단

static uint8_t sampleCounter = 0;                               // 검출 안정화를 위한 샘플 카운터

void detectRep() {
  // 위치 변화량 계산
  deltaPosition = position - prevPosition;

  // 피크 도달 여부 판단
  if (deltaPosition * prevDeltaPosition < 0) { directionChanged = true; }
  reachedPeak = (fabsf(prevDeltaPosition) > fabsf(deltaPosition)) ? true : false;

  // 기준 위치(lastPosition)로부터 현재 변화율 계산
  currentRate = fabsf(position - lastPosition);
  
  // 변화량 부호가 바뀌고 피크 조건이 맞으면
  if (directionChanged && (repCount % 2) != reachedPeak) {
    // if (sampleCounter >= 5) {
    //   sampleCounter = 0;
    // }
    
    // 사이클이 시작됐다고 판단되면 repCount 카운트 증가
    if ((repCount % 2) == 0 && currentRate >= averageRate && !movementStopped) {
      repCount++;
      averageRate = fabsf((averageRate + currentRate  * 0.47f) * 0.5f);
      lastPosition = position;
      directionChanged = false;
    }

    // 완전한 사이클(전체 rep)이 완료되면 1RM 카운트 증가
    if ((repCount % 2) == 1 && currentRate >= averageRate && movementStopped) {
      repCount++; oneRepMaxCount++;
      averageRate = fabsf((averageRate + currentRate * 0.47f) * 0.5f);
      lastPosition = position;
      directionChanged = false;

      prevVelocity = velocity = 0.0f;
      prevPosition = position = 0.0f;
    }
  }
  // sampleCounter++;
}



//
// measureUserSpeed: 횟수 당 사용자 운동시간 측정
//
static bool moveStoppedSpeed = true;                            // 운동이 정지된 상태 표시
static uint8_t counterSpeed = 0;                                // 운동 정지 판단용 카운터

bool measuring = false;                                         // 측정 상태 표시
float measuredTime = 0.0f;                                      // 사용자의 한 동작 측정 시간
uint16_t userSetTime = 3;                                       // 사용자의 한 동작 설정 시간

bool measureUserSpeed() {
  if (fabsf(lowPassSVM) <= 50) {
    moveStoppedSpeed = true;
    counterSpeed = 0;
  } else {
    counterSpeed++;
    if (counterSpeed > 5) {
      moveStoppedSpeed = false;
      counterSpeed = 0;
    }
  }

  static uint32_t repLastTime;
  if (!measuring) {
    if ((repCount % 2) == 0 && !moveStoppedSpeed) {
      repLastTime = millis();
      measuring = true;
    }
  } else {
    if (preOneRepMaxCount != oneRepMaxCount) {
      uint32_t repNowTime = millis();
      measuredTime = (repNowTime - repLastTime) / 1000.0f;
      measuring = false;
      return true;
    }
  }
  return false;
}


//
// updatePreviousState: // 다음 루프를 위한 이전 값 업데이트
//
void updatePreviousState() {
  prevLowPassSVM       = lowPassSVM;
  prevVelocity         = velocity; 
  prevLowPassVelocity  = lowPassVelocity;
  prevHighPassVelocity = highPassVelocity;
  prevPosition         = position;
  prevDeltaPosition    = deltaPosition;
  prevRepCount         = repCount;
  preOneRepMaxCount    = oneRepMaxCount;
}



//
// resetFiltersAndCounters: 인터럽트 발생 시 모든 필터와 카운터를 초기화
//
void resetFiltersAndCounters() {
  SVM = AngleX = 0.0f;
  /////////////////////////////////////////////////
  prevLowPassSVM = lowPassSVM = 0.0f;
  prevVelocity = velocity = 0.0f;
  prevHighPassVelocity = highPassVelocity = 0.0f;
  prevLowPassVelocity = lowPassVelocity = 0.0f;

  movementStopped = true;
  stabilityCounter = 0;
  /////////////////////////////////////////////////
  prevPosition = position = 0.0f;
  prevDeltaPosition = deltaPosition = 0.0f;
  averageRate = 5.0f; currentRate = 0.0f;
  lastPosition = 0.0f;
  prevRepCount = repCount = 0;
  preOneRepMaxCount = oneRepMaxCount = 0;

  directionChanged = reachedPeak = false;
  sampleCounter = 0;
  /////////////////////////////////////////////////
  measuring = false;
  measuredTime = 0.0f; 
  
  moveStoppedSpeed = true;
  counterSpeed = 0;
}



// Serial.print("reachedPeak = "); Serial.print(reachedPeak); Serial.print("      ");
// Serial.print("movementStopped = ")Serial.print(movementStopped); Serial.print("      ");