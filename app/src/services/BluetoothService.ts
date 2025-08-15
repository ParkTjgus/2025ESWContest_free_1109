import BleManager, {
  BleDisconnectPeripheralEvent,
  Peripheral,
} from "react-native-ble-manager";
import { NativeEventEmitter, NativeModules, Platform } from "react-native";
import { BluetoothServiceEvents } from "../types/ble";

const BleManagerModule = NativeModules.BleManager;
// NativeEventEmitter를 사용하여 서비스의 이벤트 시스템을 구현
const serviceEventEmitter = new NativeEventEmitter(BleManagerModule);

class BluetoothService {
  private static instance: BluetoothService;
  private isInitialized = false;

  // 싱글톤 패턴: 생성자를 private으로 막고 getInstance로만 접근 허용
  private constructor() {}

  public static getInstance(): BluetoothService {
    if (!BluetoothService.instance) {
      BluetoothService.instance = new BluetoothService();
    }
    return BluetoothService.instance;
  }

  /**
   * BleManager를 시작하고 네이티브 이벤트 리스너를 등록합니다.
   * 앱 실행 시 한 번만 호출되어야 합니다.
   */
  public initialize(): void {
    if (this.isInitialized) return;

    BleManager.start({ showAlert: false }).then(() => {
      console.log("BluetoothService: BleManager initialized");
      this.registerNativeEventListeners();
      this.isInitialized = true;
    });
  }

  /**
   * 네이티브 리스너들을 정리합니다.
   */
  public cleanup(): void {
    console.log("BluetoothService: Cleaning up listeners");
    serviceEventEmitter.removeAllListeners("BleManagerDiscoverPeripheral");
    serviceEventEmitter.removeAllListeners("BleManagerStopScan");
    serviceEventEmitter.removeAllListeners("BleManagerDisconnectPeripheral");
  }

  /**
   * BleManager가 발생시키는 네이티브 이벤트를 수신하고,
   * 우리가 정의한 더 단순한 커스텀 이벤트로 변환하여 외부에 알립니다.
   */
  private registerNativeEventListeners(): void {
    // 기기 발견 이벤트
    serviceEventEmitter.addListener(
      "BleManagerDiscoverPeripheral",
      (peripheral: Peripheral) => {
        if (peripheral.name) {
          serviceEventEmitter.emit("discover", peripheral);
        }
      }
    );

    // 스캔 중지 이벤트
    serviceEventEmitter.addListener("BleManagerStopScan", () => {
      serviceEventEmitter.emit("stopScan");
    });

    // 연결 끊김 이벤트
    serviceEventEmitter.addListener(
      "BleManagerDisconnectPeripheral",
      (event: BleDisconnectPeripheralEvent) => {
        serviceEventEmitter.emit("disconnect", event);
      }
    );
  }

  /**
   * 주변 기기 스캔을 시작합니다.
   */
  public async startScan(): Promise<void> {
    const isBluetoothOn = await BleManager.checkState();
    if (isBluetoothOn !== "on") {
      // 이 부분은 스토어에서 처리하므로 여기선 에러를 던지거나 반환할 수 있습니다.
      throw new Error("블루투스를 켜주세요.");
    }

    // 이미 본딩된 기기 목록을 먼저 'discover' 이벤트로 전달
    const bondedDevices = await BleManager.getBondedPeripherals();
    bondedDevices.forEach((device) => {
      serviceEventEmitter.emit("discover", device);
    });

    await BleManager.scan([], 5, true);
  }

  /**
   * 특정 기기에 연결하고, 필요 시 본딩을 생성합니다.
   */
  public async connect(peripheral: Peripheral): Promise<void> {
    await BleManager.connect(peripheral.id);
    if (Platform.OS === "android") {
      // 1. 전체 본딩 목록을 가져옵니다.
      const bondedList = await BleManager.getBondedPeripherals();
      // 2. 목록에 현재 기기가 있는지 확인합니다.
      const isAlreadyBonded = bondedList.some(
        (bondedDevice) => bondedDevice.id === peripheral.id
      );

      if (!isAlreadyBonded) {
        await BleManager.createBond(peripheral.id);
      }
    }
  }

  /**
   * 기기와의 연결을 해제합니다.
   */
  public async disconnect(peripheralId: string): Promise<void> {
    await BleManager.disconnect(peripheralId);
  }

  /**
   * 커스텀 이벤트를 구독(listen)하기 위한 메서드
   */
  public addListener<E extends keyof BluetoothServiceEvents>(
    event: E,
    listener: (data: BluetoothServiceEvents[E]) => void
  ) {
    return serviceEventEmitter.addListener(event, listener);
  }
}

// 싱글턴 인스턴스를 export
export const bluetoothService = BluetoothService.getInstance();
