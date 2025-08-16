import { NativeEventEmitter, NativeModules, Platform } from "react-native";
import BleManager, {
  BleDisconnectPeripheralEvent,
  Peripheral,
} from "react-native-ble-manager";
import { useBleStore } from "../stores/useBleStore";
import { permissionService } from "../services/PermissionService";

const BleManagerModule = NativeModules.BleManager;
const bleManagerEmitter = new NativeEventEmitter(BleManagerModule);

class BluetoothService {
  private static instance: BluetoothService;
  private isInitialized = false;

  private constructor() {}

  public static getInstance(): BluetoothService {
    if (!BluetoothService.instance) {
      BluetoothService.instance = new BluetoothService();
    }
    return BluetoothService.instance;
  }

  public async initialize(): Promise<void> {
    if (this.isInitialized) return;

    await BleManager.start({ showAlert: false });
    console.log("BluetoothService: BleManager initialized");

    this.registerNativeEventListeners();
    this.isInitialized = true;
  }

  public cleanup(): void {
    console.log("BluetoothService: Cleaning up listeners");
    bleManagerEmitter.removeAllListeners("BleManagerDiscoverPeripheral");
    bleManagerEmitter.removeAllListeners("BleManagerStopScan");
    bleManagerEmitter.removeAllListeners("BleManagerDisconnectPeripheral");
    bleManagerEmitter.removeAllListeners("BleManagerDidUpdateState");
  }

  private registerNativeEventListeners(): void {
    bleManagerEmitter.addListener(
      "BleManagerDiscoverPeripheral",
      (peripheral: Peripheral) => {
        console.log("🔵 Discovered peripheral:", {
          id: peripheral.id,
          name: peripheral.name || "Unknown",
          rssi: peripheral.rssi,
          advertising: peripheral.advertising,
        });
        useBleStore.getState()._addDiscoveredDevice(peripheral);
      }
    );

    bleManagerEmitter.addListener("BleManagerStopScan", () => {
      console.log("🔴 Native: Scan stopped event received.");
      useBleStore.getState()._handleScanStop();
    });

    bleManagerEmitter.addListener(
      "BleManagerDisconnectPeripheral",
      (event: BleDisconnectPeripheralEvent) => {
        console.log("❌ Device disconnected:", event);
        useBleStore.getState()._handleDisconnection(event);
      }
    );
  }

  // PermissionService를 사용한 권한 확인
  private async requestPermissions(): Promise<boolean> {
    if (Platform.OS === "android") {
      try {
        console.log("Requesting BLUETOOTH_SCAN permission...");
        const scanGranted = await permissionService.requestScanPermission();
        console.log(
          "BLUETOOTH_SCAN permission:",
          scanGranted ? "GRANTED" : "DENIED"
        );

        console.log("Requesting BLUETOOTH_CONNECT permission...");
        const connectGranted =
          await permissionService.requestConnectPermission();
        console.log(
          "BLUETOOTH_CONNECT permission:",
          connectGranted ? "GRANTED" : "DENIED"
        );

        // 만약 PermissionService에 requestLocationPermission()이 있다면 사용
        const locationGranted =
          await permissionService.requestLocationPermission();
        console.log(
          "ACCESS_FINE_LOCATION permission:",
          locationGranted ? "GRANTED" : "DENIED"
        );

        const allGranted = scanGranted && connectGranted; // && locationGranted;
        console.log("All Bluetooth permissions granted:", allGranted);

        if (!allGranted) {
          console.error("블루투스 권한이 거부되었습니다.");
          console.error(
            "설정 > 앱 > [앱이름] > 권한에서 블루투스 및 위치 권한을 허용해주세요."
          );
        }

        return allGranted;
      } catch (error) {
        console.error("Error requesting permissions:", error);
        return false;
      }
    }
    return true; // iOS는 true 반환
  }

  public async startScan(): Promise<void> {
    try {
      // 1. 권한 확인
      console.log("StartScan: registering listeners before scan...");
      this.registerNativeEventListeners(); // 스캔 전에 강제 등록

      const hasPermissions = await this.requestPermissions();
      if (!hasPermissions) {
        throw new Error("블루투스 스캔에 필요한 권한이 없습니다.");
      }

      // 2. 블루투스 상태 확인
      const isBluetoothOn = await BleManager.checkState();
      if (isBluetoothOn !== "on") {
        throw new Error("블루투스를 켜주세요.");
      }

      // 3. 이전 스캔이 진행 중이면 중지
      try {
        await BleManager.stopScan();
      } catch (error) {
        // 스캔이 진행 중이 아니면 에러가 발생할 수 있으므로 무시
        console.log("No active scan to stop");
      }

      // 4. 기존 디바이스 목록 초기화 (bonded 기기들만 남기기 위해)
      // useBleStore.getState().devices = []; // 이 라인을 제거하거나 주석처리

      // 5. 본딩된 디바이스들을 먼저 추가
      const bondedDevices = await BleManager.getBondedPeripherals();
      console.log("Bonded devices:", bondedDevices);

      const devicesWithBondedFlag = bondedDevices.map((device) => ({
        ...device,
        bonded: true,
      }));

      devicesWithBondedFlag.forEach((device) => {
        useBleStore.getState()._addDiscoveredDevice(device);
      });

      // 6. 새로운 스캔 시작 - 파라미터 단순화
      console.log("Starting BLE scan...");

      bleManagerEmitter.addListener(
        "BleManagerDiscoverPeripheral",
        (peripheral) => {
          console.log("🔵 Discovered peripheral:", peripheral);
        }
      );
      await BleManager.scan(
        [], // 서비스 UUID 필터 (빈 배열 = 모든 디바이스)
        30, // 스캔 시간
        false // 중복 허용 -> false로 변경
      );

      console.log("BLE scan started successfully");
    } catch (error) {
      console.error("Error starting scan:", error);
      throw error;
    }
  }

  public async connect(peripheral: Peripheral): Promise<void> {
    await BleManager.connect(peripheral.id);
    if (Platform.OS === "android") {
      const bondedList = await BleManager.getBondedPeripherals();
      const isAlreadyBonded = bondedList.some(
        (bondedDevice) => bondedDevice.id === peripheral.id
      );

      if (!isAlreadyBonded) {
        await BleManager.createBond(peripheral.id);
      }
    }
  }

  public async disconnect(peripheralId: string): Promise<void> {
    await BleManager.disconnect(peripheralId);
  }

  // 디버깅을 위한 메서드 추가
  public async getConnectedPeripherals(): Promise<Peripheral[]> {
    return await BleManager.getConnectedPeripherals([]);
  }

  public async isScanning(): Promise<boolean> {
    return await BleManager.isScanning();
  }
}

export const bluetoothService = BluetoothService.getInstance();
