// store/useBleStore.ts
import { create } from "zustand";
import {
  BleDisconnectPeripheralEvent,
  Peripheral,
} from "react-native-ble-manager";
import { BluetoothState } from "../types/ble";
import { permissionService } from "../services/PermissionService";
import { bluetoothService } from "../services/BluetoothService";

export const useBleStore = create<BluetoothState>((set, get) => ({
  status: "idle",
  devices: [],
  connectedDevice: null,
  error: null,

  initialize: () => {
    // 서비스 초기화만 호출하면, 서비스가 알아서 이벤트 리스너를 등록하고
    // 스토어의 상태를 직접 업데이트합니다.
    bluetoothService.initialize();
  },

  cleanup: () => {
    bluetoothService.cleanup();
  },

  startScan: async () => {
    const hasScanPermission = await permissionService.requestScanPermission();
    const hasConnectPermission =
      await permissionService.requestConnectPermission();
    if (!hasScanPermission || !hasConnectPermission) {
      set({
        status: "error",
        error: "블루투스 스캔 및 연결 권한이 필요합니다.",
      });
      return;
    }

    set({ devices: [], status: "scanning", error: null });
    try {
      await bluetoothService.startScan();
    } catch (e: any) {
      set({ status: "error", error: e.message });
    }
  },

  connectToDevice: async (peripheral: Peripheral) => {
    set({ status: "connecting" });
    try {
      await bluetoothService.connect(peripheral);
      set({ status: "connected", connectedDevice: peripheral, error: null });
    } catch (error) {
      set({ status: "error", error: "연결 또는 본딩에 실패했습니다." });
    }
  },

  disconnectDevice: async () => {
    const { connectedDevice } = get();
    if (connectedDevice) {
      try {
        await bluetoothService.disconnect(connectedDevice.id);
        set({ status: "idle", connectedDevice: null });
      } catch (error) {
        set({ status: "error", error: "연결 해제에 실패했습니다." });
      }
    }
  },

  // --- 내부 상태 변경 함수 (서비스가 직접 호출) ---
  _addDiscoveredDevice: (device: Peripheral) => {
    set((state) => {
      if (!state.devices.some((d) => d.id === device.id)) {
        return { devices: [...state.devices, device] };
      }
      return {};
    });
  },

  _handleScanStop: () => {
    set((state) => {
      if (state.status === "scanning") {
        return { status: "idle" };
      }
      return {};
    });
  },

  _handleDisconnection: (event: BleDisconnectPeripheralEvent) => {
    const { connectedDevice } = get();
    if (connectedDevice?.id === event.peripheral) {
      set({
        status: "error",
        connectedDevice: null,
        error: "기기와의 연결이 끊어졌습니다.",
      });
    }
  },
}));
