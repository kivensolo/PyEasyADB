package com.easyadb.core.config

import com.easyadb.core.log.AppLogger
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 使用 javax.xml.parsers.DocumentBuilder 的 XML 配置文件加载器。
 * 对应 Python 中从 res/config/ 加载 XML 的逻辑。
 */
object XmlConfigLoader {

    private val logger = AppLogger.getLogger(XmlConfigLoader::class.java)

    private val factory = DocumentBuilderFactory.newInstance().apply {
        isIgnoringComments = true
        isIgnoringElementContentWhitespace = true
    }

    /**
     * 加载 cmdConfig.xml 到 CmdGroup 列表。
     */
    fun loadCmdConfig(file: File): List<CmdGroup> {
        if (!file.exists()) {
            logger.warn { "cmdConfig.xml not found at ${file.absolutePath}" }
            return emptyList()
        }
        val db = factory.newDocumentBuilder().parse(file)
        val groups = db.documentElement.getElementsByTagName("group")
        val result = mutableListOf<CmdGroup>()

        for (i in 0 until groups.length) {
            val groupElement = groups.item(i) as Element
            val groupName = groupElement.getAttribute("name")
            val items = mutableListOf<CmdItem>()
            val subGroups = mutableListOf<CmdSubGroup>()

            for (j in 0 until groupElement.childNodes.length) {
                val child = groupElement.childNodes.item(j)
                if (child !is Element) continue

                when (child.tagName) {
                    "item" -> {
                        val item = parseCmdItem(child)
                        if (item != null) items.add(item)
                    }
                    "sub_group" -> {
                        val subGroup = parseCmdSubGroup(child)
                        if (subGroup != null) subGroups.add(subGroup)
                    }
                }
            }
            result.add(CmdGroup(name = groupName, items = items, subGroups = subGroups))
        }
        return result
    }

    /**
     * 加载 function_templates.xml 到 FunctionTemplate 列表。
     */
    fun loadFunctionTemplates(file: File): List<FunctionTemplate> {
        if (!file.exists()) {
            logger.warn { "function_templates.xml not found at ${file.absolutePath}" }
            return emptyList()
        }
        val db = factory.newDocumentBuilder().parse(file)
        val templateNodes = db.documentElement.getElementsByTagName("template")
        val result = mutableListOf<FunctionTemplate>()

        for (i in 0 until templateNodes.length) {
            val templateElement = templateNodes.item(i) as Element
            val name = templateElement.getAttribute("name")
            val layout = templateElement.getAttribute("layout").ifEmpty { "grid" }
            val items = mutableListOf<FunctionItem>()

            val itemNodes = templateElement.getElementsByTagName("item")
            for (j in 0 until itemNodes.length) {
                val itemElement = itemNodes.item(j) as Element
                val state = itemElement.getAttribute("state")
                val attrs = parseAttrs(itemElement)
                items.add(
                    FunctionItem(
                        icon = attrs["icon"] ?: "",
                        text = attrs["text"] ?: "",
                        cmd = attrs["cmd"] ?: "",
                        action = attrs["act"] ?: "",
                        shell = attrs["shell"]?.toBoolean() ?: true,
                        needPkgName = attrs["isNeedPkgName"]?.toBoolean() ?: false,
                        needDeviceOnline = attrs["isNeedDeviceOnline"]?.toBoolean() ?: false,
                        state = state
                    )
                )
            }
            result.add(FunctionTemplate(name = name, layout = layout, items = items))
        }
        return result
    }

    /**
     * 加载 menus_ui.xml 到 MenuConfig 列表。
     */
    fun loadMenuConfig(file: File): List<MenuConfig> {
        if (!file.exists()) {
            logger.warn { "menus_ui.xml not found at ${file.absolutePath}" }
            return emptyList()
        }
        val db = factory.newDocumentBuilder().parse(file)
        val menuNodes = db.documentElement.getElementsByTagName("menu")
        val result = mutableListOf<MenuConfig>()

        for (i in 0 until menuNodes.length) {
            val menuElement = menuNodes.item(i) as Element
            val menuName = menuElement.getAttribute("name")
            val actions = mutableListOf<MenuAction>()

            val actionNodes = menuElement.getElementsByTagName("action")
            for (j in 0 until actionNodes.length) {
                val actionElement = actionNodes.item(j) as Element
                val actionName = actionElement.getAttribute("name")
                val state = actionElement.getAttribute("state")
                val attrs = parseAttrs(actionElement)
                actions.add(
                    MenuAction(
                        name = actionName,
                        shortcut = attrs["shortcut"] ?: "",
                        icon = attrs["icon"] ?: "",
                        action = attrs["action"] ?: "",
                        state = state
                    )
                )
            }
            result.add(MenuConfig(name = menuName, actions = actions))
        }
        return result
    }

    private fun parseCmdItem(element: Element): CmdItem? {
        val name = element.getAttribute("name")
        if (name.isBlank()) return null
        val text = element.textContent?.trim() ?: ""
        val desc = element.getAttribute("desc")
        val dstPkg = element.getAttribute("dst_pkg").toBoolean()
        // <item> 上的 shell 属性，非 "false" 时默认为 true
        val shell = element.getAttribute("shell") != "false"
        return CmdItem(name = name, cmd = text, shell = shell, needDstPkg = dstPkg, description = desc)
    }

    private fun parseCmdSubGroup(element: Element): CmdSubGroup? {
        val name = element.getAttribute("name")
        if (name.isBlank()) return null
        val items = mutableListOf<CmdItem>()
        val itemNodes = element.getElementsByTagName("item")
        for (i in 0 until itemNodes.length) {
            val item = parseCmdItem(itemNodes.item(i) as Element)
            if (item != null) items.add(item)
        }
        return CmdSubGroup(name = name, items = items)
    }

    private fun parseAttrs(element: Element): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val attrNodes = element.getElementsByTagName("attr")
        for (i in 0 until attrNodes.length) {
            val attrElement = attrNodes.item(i) as Element
            val attrName = attrElement.getAttribute("name")
            val attrValue = attrElement.textContent?.trim() ?: ""
            if (attrName.isNotBlank()) {
                attrs[attrName] = attrValue
            }
        }
        return attrs
    }
}
