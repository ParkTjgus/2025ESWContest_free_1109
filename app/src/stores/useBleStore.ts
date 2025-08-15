import { create } from "zustand";
import {
  BleDisconnectPeripheralEvent,
  Peripheral,
} from "react-native-ble-manager";
import { BluetoothState } from "../types/ble";
import { permissionService } from "../services/PermissionService";
import { bluetoothService } from "../services/BluetoothService";

export const useBleStore = create<BluetoothState>((set, get) => ({
  // --- 상태 ---
  status: "idle",
  devices: [],
  connectedDevice: null,
  error: null,

  // --- 액션 ---

  /**
   * 스토어와 서비스를 연결하는 초기화 함수. 앱 시작 시 호출되어야 합니다.
   */
  initialize: () => {
    bluetoothService.initialize(); // 서비스의 네이티브 리스너 등록

    // BluetoothService가 보내는 커스텀 이벤트를 구독하여 상태를 업데이트합니다.
    bluetoothService.addListener("discover", (peripheral) => {
      get()._addDiscoveredDevice(peripheral);
    });

    bluetoothService.addListener("stopScan", () => {
      get()._handleScanStop();
    });

    bluetoothService.addListener("disconnect", (event) => {
      get()._handleDisconnection(event);
    });
  },

  /**
   * 리스너 정리 함수. 앱 종료 시 호출되어야 합니다.
   */
  cleanup: () => {
    bluetoothService.cleanup();
  },

  startScan: async () => {
    // 👇 사용자님 의견대로, 스캔 시작 시 두 권한을 모두 확인합니다.
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
      // 이제 이 함수는 필요한 모든 권한을 가진 상태에서 호출됩니다.
      await bluetoothService.startScan();
    } catch (e: any) {
      set({ status: "error", error: e.message });
    }
  },

  connectToDevice: async (peripheral: Peripheral) => {
    // 👇 이제 여기서는 권한을 다시 요청할 필요가 없습니다.
    // 스캔 과정에서 이미 권한을 받았기 때문입니다.
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

  // --- 내부 상태 변경 함수 (이벤트 리스너가 호출) ---

  _addDiscoveredDevice: (device: Peripheral) => {
    set((state) => {
      if (!state.devices.some((d) => d.id === device.id)) {
        return { devices: [...state.devices, device] };
      }
      return {}; // 상태 변경 없음
    });
  },

  _handleScanStop: () => {
    set((state) => {
      // 스캔 중에만 idle로 변경 (다른 상태를 덮어쓰지 않기 위함)
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
