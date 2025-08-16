// types/ble.ts
import {
  Peripheral,
  BleDisconnectPeripheralEvent,
} from "react-native-ble-manager";

// 👇 서비스가 방송할 이벤트의 종류를 정의합니다.
export type BluetoothServiceEvents = {
  discover: Peripheral;
  stopScan: void;
  disconnect: BleDisconnectPeripheralEvent;
};

export type Status = "idle" | "scanning" | "connecting" | "connected" | "error";

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

  _addDiscoveredDevice: (device: Peripheral) => void;
  _handleScanStop: () => void;
  _handleDisconnection: (event: BleDisconnectPeripheralEvent) => void;
}
