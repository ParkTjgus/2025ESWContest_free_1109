// 테스트 최상단에 react-native 먼저 mock
jest.mock("react-native", () => ({
  NativeEventEmitter: jest.fn(),
  NativeModules: { BleManager: {} },
  Platform: { OS: "android" },
}));

// BleManager 모듈 mock
jest.mock("react-native-ble-manager", () => ({
  __esModule: true,
  default: {
    start: jest.fn(),
    scan: jest.fn(),
    connect: jest.fn().mockResolvedValue(undefined),
    disconnect: jest.fn(),
    getBondedPeripherals: jest.fn(),
    createBond: jest.fn().mockResolvedValue(undefined),
  },
}));

import BleManager, { Peripheral } from "react-native-ble-manager";
import { bluetoothService } from "../services/BluetoothService";

// 테스트용 mock peripheral
const mockPeripheral: Peripheral = {
  id: "00:11:22:33:44:55",
  name: "Test-Device",
  rssi: -50,
  advertising: {},
};

const mockAlreadyBondedDevice: Peripheral = {
  id: "AA:BB:CC:DD:EE:FF",
  name: "Bonded-Device",
  rssi: -60,
  advertising: {},
};

describe("BluetoothService", () => {
  beforeEach(() => {
    (BleManager.connect as jest.Mock).mockClear();
    (BleManager.getBondedPeripherals as jest.Mock).mockClear();
    (BleManager.createBond as jest.Mock).mockClear();
  });

  describe("connect", () => {
    it("이미 본딩된 기기는 createBond 호출 안 함", async () => {
      (BleManager.getBondedPeripherals as jest.Mock).mockResolvedValue([
        mockPeripheral,
      ]);

      await bluetoothService.connect(mockPeripheral);

      expect(BleManager.connect).toHaveBeenCalledWith(mockPeripheral.id);
      expect(BleManager.getBondedPeripherals).toHaveBeenCalled();
      expect(BleManager.createBond).not.toHaveBeenCalled();
    });

    it("본딩 안 된 기기는 createBond 호출", async () => {
      (BleManager.getBondedPeripherals as jest.Mock).mockResolvedValue([
        mockAlreadyBondedDevice,
      ]);

      await bluetoothService.connect(mockPeripheral);

      expect(BleManager.connect).toHaveBeenCalledWith(mockPeripheral.id);
      expect(BleManager.getBondedPeripherals).toHaveBeenCalled();
      expect(BleManager.createBond).toHaveBeenCalledWith(mockPeripheral.id);
    });

    it("연결 실패 시 에러 발생", async () => {
      const errorMessage = "Connection failed";
      (BleManager.connect as jest.Mock).mockRejectedValue(
        new Error(errorMessage)
      );

      await expect(bluetoothService.connect(mockPeripheral)).rejects.toThrow(
        errorMessage
      );

      expect(BleManager.getBondedPeripherals).not.toHaveBeenCalled();
      expect(BleManager.createBond).not.toHaveBeenCalled();
    });
  });
});
