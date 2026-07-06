package com.easyadb.ui.devicelist

import com.easyadb.core.config.CmdGroup
import com.easyadb.core.config.CmdItem
import com.easyadb.core.config.CmdSubGroup
import com.easyadb.core.database.DeviceRecord
import com.easyadb.core.device.DeviceInfo
import com.easyadb.core.device.DeviceState

/**
 * 设备/命令树的统一节点抽象。
 *
 * 对应 Python 中 QStandardItem + TreeItemType 三种类型：
 *   TYPE_ROOT_DEVICE = 1  → [DeviceGroupRootNode] / [CommandGroupRootNode]
 *   TYPE_DEVICE     = 2   → [DeviceNode]
 *   TYPE_ADB_CMD    = 3   → [CommandNode]
 *
 * 另引入 [CommandSubGroupNode] 对应 cmdConfig.xml 中的 `<sub_group>`。
 */
sealed class TreeNode {
    abstract val id: String
    abstract val depth: Int

    /** 设备分组根节点：「设备列表」。 */
    data class DeviceGroupRootNode(
        override val id: String = "root.devices",
        override val depth: Int = 0,
        val displayName: String = "设备列表"
    ) : TreeNode()

    /** 单个设备节点：ip:port(别名)。 */
    data class DeviceNode(
        val record: DeviceRecord,
        val online: Boolean,
        val state: DeviceState?,
        override val depth: Int = 1
    ) : TreeNode() {
        override val id: String get() = "device.${record.ip}"

        /** 显示文本，对齐 Python 中 init_tree_view 的拼装逻辑。 */
        val displayText: String
            get() {
                val addr = if (record.port == "0") record.ip else "${record.ip}:${record.port}"
                return if (record.alias.isBlank()) addr else "$addr(${record.alias})"
            }

        /** 设备是否已在线（在线 = state 为 device/offline，可执行 ADB 命令）。 */
        val isOnline: Boolean get() = online && (state == DeviceState.DEVICE || state == DeviceState.OFFLINE)
    }

    /** 命令分组根节点：「命令列表」。 */
    data class CommandGroupRootNode(
        override val id: String = "root.commands",
        override val depth: Int = 0,
        val displayName: String = "命令列表"
    ) : TreeNode()

    /** cmdConfig.xml 中的 `<group>`。 */
    data class CommandGroupNode(
        val group: CmdGroup,
        override val depth: Int = 1
    ) : TreeNode() {
        override val id: String get() = "cmd.group.${group.name}"
    }

    /** cmdConfig.xml 中的 `<sub_group>`。 */
    data class CommandSubGroupNode(
        val parentGroupName: String,
        val subGroup: CmdSubGroup,
        override val depth: Int = 2
    ) : TreeNode() {
        override val id: String get() = "cmd.sub.${parentGroupName}.${subGroup.name}"
    }

    /** cmdConfig.xml 中的 `<item>`。 */
    data class CommandNode(
        val parentGroupId: String,
        val item: CmdItem,
        override val depth: Int
    ) : TreeNode() {
        override val id: String get() = "cmd.item.${parentGroupId}.${item.name}"
    }
}

/**
 * 从 [records]（数据库）与 [onlineDevices]（DeviceWatcher）合并出设备节点列表。
 *
 * 对应 Python MainWindow.init_tree_view 中的拼装：
 *   - 数据库中保存的设备始终展示（含离线）
 *   - DeviceWatcher 推送的 state 用于驱动图标
 */
fun buildDeviceNodes(
    records: List<DeviceRecord>,
    onlineDevices: List<DeviceInfo>
): List<TreeNode.DeviceNode> {
    val stateByName = onlineDevices.associateBy { it.name }
    return records.sortedWith(compareBy({ it.ip }, { it.port })).map { record ->
        val portPart = if (record.port == "0") "" else ":${record.port}"
        val lookupKey = record.ip + portPart
        val matched = stateByName[lookupKey]
        TreeNode.DeviceNode(
            record = record,
            online = matched != null,
            state = matched?.state
        )
    }
}

/**
 * 从 [groups]（cmdConfig.xml）展开为命令节点树（含分组与子分组）。
 */
fun buildCommandNodes(groups: List<CmdGroup>): List<Pair<TreeNode, List<TreeNode>>> {
    val result = mutableListOf<Pair<TreeNode, List<TreeNode>>>()
    for (group in groups) {
        val groupNode = TreeNode.CommandGroupNode(group = group)
        val children = mutableListOf<TreeNode>()
        for (sub in group.subGroups) {
            children.add(TreeNode.CommandSubGroupNode(parentGroupName = group.name, subGroup = sub))
        }
        for (item in group.items) {
            children.add(
                TreeNode.CommandNode(
                    parentGroupId = group.name,
                    item = item,
                    depth = 2
                )
            )
        }
        result.add(groupNode to children)
    }
    return result
}
