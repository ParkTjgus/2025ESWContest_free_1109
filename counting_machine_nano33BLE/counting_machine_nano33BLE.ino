#include "counting_machine.h"

// 보드 핀 설정
const uint8_t LED_PIN_L = 23;
const uint8_t LED_PIN_R = 26;
const uint8_t BUTTON_PIN = 21;
const uint8_t BUZZER_PIN = 24;


void setup() {
  Serial.begin(115200);
  while (!Serial) delay(10);  // USB CDC 기반에서 시리얼 안정화
  
  // 핀 설정
  pinMode(LED_PIN_L, OUTPUT);
  pinMode(LED_PIN_R, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  
  // 버튼에 FALLING 엣지 인터럽트를 연결하여 상태 초기화 실행
  attachInterrupt(digitalPinToInterrupt(BUTTON_PIN), resetFiltersAndCounters, FALLING);
  
  // IMU 초기화 및 연결 확인
  if (!IMU.begin()) {
    Serial.println("Failed to initialize IMU!"); while (1);
  } Serial.println("Succeed to initialize IMU!");

  // Serial.print("Accelerometer sample rate = ");
  // Serial.print(IMU.accelerationSampleRate());
  // Serial.println("Hz");
  // Serial.print("Gyroscope sample rate = ");
  // Serial.print(IMU.gyroscopeSampleRate());
  // Serial.println("Hz");

  // 기준 가속도 업데이트
  Serial.println("Sensor stabilizing...");
  updateBaseline(300);
  Serial.println("Sensor is ready!");
  delay(1);
}


void loop() {
  static uint32_t lastTime = micros(); // 시간 측정 (마이크로초)
  uint32_t nowTime = micros();
  float dt = (nowTime - lastTime) / 1000000.0; // 지난 시간 dt (초) 계산
  lastTime = nowTime;

  // 운동 처리
  readSensorData(dt); // 센서 데이터 업데이트

  const float THRESH_ANGLE = 10.0f;
  digitalWrite(LED_PIN_L, (AngleX >  THRESH_ANGLE) ? HIGH : LOW);
  digitalWrite(LED_PIN_R, (AngleX < -THRESH_ANGLE) ? HIGH : LOW);

  processMotion(dt); // 필터링 및 적분: 속도와 위치 갱신
  detectRep(); // 위치 변화 기반 운동 반복(Rep) 검출

  if (preOneRepMaxCount != oneRepMaxCount) {
    if (measuredSpeed > userSetSpeed) {
      tone(BUZZER_PIN, 440, 800);
    } else {
      tone(BUZZER_PIN, 262, 200);
    }
  }

  // 다음 루프를 위한 이전 값 업데이트
  prevLowPassSVM = lowPassSVM;
  prevVelocity = velocity;
  prevLowPassVelocity = lowPassVelocity;
  prevHighPassVelocity = highPassVelocity;
  prevPosition = position;
  prevDeltaPosition = deltaPosition;
  prevRepCount = repCount;
  preOneRepMaxCount = oneRepMaxCount;
  
  static uint8_t cnt_loop = 0;
  if (++cnt_loop < 1) return;
  cnt_loop = 0;

  // 디버그 출력 (Serial 모니터)
  // Serial.print(ax, 5); Serial.print("      ");
  // Serial.print(ay, 5); Serial.print("      ");
  // Serial.print(az, 5); Serial.print("      ");

  // Serial.print(gx, 5); Serial.print("      ");
  // Serial.print(gy, 5); Serial.print("      ");
  // Serial.print(gz, 5); Serial.print("      ");

  // Serial.print(ac_AngleX, 5); Serial.print("      ");
  // Serial.print(gy_AngleX, 5); Serial.print("      ");
  Serial.print(AngleX, 5); Serial.print("      ");

  //Serial.print(SVM, 5); Serial.print("      ");
  Serial.print(lowPassSVM, 5); Serial.print("      ");
  Serial.print(position, 5); Serial.print("      ");

  Serial.print(repCount); Serial.print("      ");
  Serial.print(oneRepMaxCount); Serial.print("      ");
  // Serial.print(averageRate); Serial.print("      ");
  // Serial.print(currentRate); Serial.print("      ");

  // Serial.print(userSetSpeed); Serial.print("      ");
  // Serial.print(measuredSpeed); Serial.print("      ");
  Serial.println();
}