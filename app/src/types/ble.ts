import { Peripheral } from "react-native-ble-manager";
import { BleDisconnectPeripheralEvent } from "react-native-ble-manager";

/** UI 상태를 나타내는 타입 */
export type Status = "idle" | "scanning" | "connecting" | "connected" | "error";

/** Zustand 스토어의 전체 상태 및 액션에 대한 타입 */
export interface BluetoothState {
  status: Status;
  devices: Peripheral[];
  connectedDevice: Peripheral | null;
  error: string | null;

  initialize: () => void;
  cleanup: () => void;
  startScan: () => void;
  connectToDevice: (peripheral: Peripheral) => Promise<void>;
  disconnectDevice: () => void;

  // 내부 상태 변경 함수
  _addDiscoveredDevice: (device: Peripheral) => void;
  _handleScanStop: () => void;
  _handleDisconnection: (event: BleDisconnectPeripheralEvent) => void;
}

export type BluetoothServiceEvents = {
  discover: Peripheral;
  stopScan: void;
  disconnect: BleDisconnectPeripheralEvent;
};
