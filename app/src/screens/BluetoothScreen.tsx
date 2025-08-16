import React from "react";
import {
  View,
  Text,
  Button,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  SafeAreaView,
} from "react-native";
import { useBleStore } from "../stores/useBleStore"; // 스토어 경로는 실제 프로젝트에 맞게 수정하세요.
import { Peripheral } from "react-native-ble-manager";

const BluetoothScreen = () => {
  // 스토어에서 렌더링에 필요한 상태들을 구독합니다.
  const status = useBleStore((s) => s.status);
  const devices = useBleStore((s) => s.devices);
  const connectedDevice = useBleStore((s) => s.connectedDevice);
  const error = useBleStore((s) => s.error);

  // 스토어의 액션 함수들을 가져옵니다.
  const { startScan, connectToDevice, disconnectDevice } =
    useBleStore.getState();

  // FlatList의 각 항목을 렌더링하는 함수
  const renderItem = ({
    item,
  }: {
    item: Peripheral & { bonded?: boolean };
  }) => (
    // 'bonded' 속성을 확인하여 스타일을 다르게 적용합니다.
    <TouchableOpacity
      style={[styles.deviceItem, item.bonded && styles.bondedDeviceItem]}
      onPress={() => connectToDevice(item)}
      disabled={status === "connecting"}
    >
      <Text style={styles.deviceName}>
        {item.name || "Unknown Device"}
        {/* 본딩된 기기 옆에 아이콘을 표시하여 구분합니다. */}
        {item.bonded && " 👑"}
      </Text>
      <Text style={styles.deviceId}>{item.id}</Text>
      {/* RSSI 값도 표시 */}
      <Text style={styles.deviceRssi}>RSSI: {item.rssi}</Text>
    </TouchableOpacity>
  );

  // 현재 상태에 맞는 텍스트를 반환하는 헬퍼 함수
  const getStatusText = () => {
    switch (status) {
      case "scanning":
        return "주변 기기를 찾고 있습니다...";
      case "connecting":
        return "기기에 연결 중입니다...";
      case "connected":
        return `✅ ${
          connectedDevice?.name || connectedDevice?.id
        }에 연결되었습니다.`;
      case "error":
        return `❌ 오류: ${error}`;
      default:
        return "시작 버튼을 눌러 블루투스 기기를 찾아보세요.";
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* --- 상단 상태 표시 영역 --- */}
      <View style={styles.header}>
        <Text style={styles.statusText}>{getStatusText()}</Text>
        {(status === "scanning" || status === "connecting") && (
          <ActivityIndicator color="#007BFF" />
        )}
      </View>

      {/* --- 디버깅 정보 --- */}
      <View style={styles.debugInfo}>
        <Text style={styles.debugText}>
          발견된 기기: {devices.length}개 | 상태: {status}
        </Text>
      </View>

      {/* --- 중앙 액션 버튼 영역 --- */}
      <View style={styles.actionZone}>
        {connectedDevice ? (
          <Button
            title="연결 해제"
            onPress={disconnectDevice}
            color="#E53935"
          />
        ) : (
          <Button
            title="기기 스캔"
            onPress={startScan}
            disabled={status === "scanning" || status === "connecting"}
          />
        )}
      </View>

      {/* --- 기기 목록 영역 --- */}
      <FlatList
        data={devices}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        style={styles.list}
        ListEmptyComponent={
          status !== "scanning" ? (
            <Text style={styles.emptyText}>발견된 기기가 없습니다.</Text>
          ) : null
        }
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F5F5F5",
  },
  header: {
    padding: 20,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    minHeight: 60,
  },
  statusText: {
    fontSize: 18,
    textAlign: "center",
    marginRight: 10,
    color: "#333",
  },
  debugInfo: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    backgroundColor: "#E8F4FD",
  },
  debugText: {
    fontSize: 14,
    color: "#666",
    textAlign: "center",
  },
  actionZone: {
    paddingHorizontal: 20,
    marginBottom: 20,
  },
  list: {
    flex: 1,
    paddingHorizontal: 20,
  },
  deviceItem: {
    padding: 15,
    marginBottom: 10,
    backgroundColor: "white",
    borderRadius: 8,
    elevation: 2,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
  },
  // 본딩된 기기를 위한 추가 스타일
  bondedDeviceItem: {
    backgroundColor: "#E3F2FD", // 하늘색 배경
    borderColor: "#007BFF",
    borderWidth: 1,
  },
  deviceName: {
    fontSize: 16,
    fontWeight: "bold",
  },
  deviceId: {
    fontSize: 12,
    color: "gray",
    marginTop: 4,
  },
  deviceRssi: {
    fontSize: 10,
    color: "#999",
    marginTop: 2,
  },
  emptyText: {
    textAlign: "center",
    marginTop: 50,
    color: "gray",
    fontSize: 16,
  },
});

export default BluetoothScreen;
