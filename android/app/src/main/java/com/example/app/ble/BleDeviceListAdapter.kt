package com.example.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R // Make sure this R is correctly imported from your app's package

// SuppressMissingPermission is used because BluetoothDevice.name and .address
// require BLUETOOTH_CONNECT permission on API 31+, which should be checked
// before calling these properties. The adapter itself doesn't handle permissions.
@SuppressLint("MissingPermission")
class BleDeviceListAdapter(
    private val devices: MutableList<BluetoothDevice>,
    private val onItemClicked: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceListAdapter.DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceName.text = device.name ?: "Unknown Device"
        holder.deviceAddress.text = device.address
        holder.itemView.setOnClickListener {
            onItemClicked(device)
        }
    }

    override fun getItemCount(): Int = devices.size

    fun addDevice(device: BluetoothDevice) {
        if (!devices.any { it.address == device.address }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.deviceNameTextView)
        val deviceAddress: TextView = itemView.findViewById(R.id.deviceAddressTextView)
    }
}
