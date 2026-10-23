package com.easyadb.core.config

import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/23
 * description：XmlConfigLoader 单测——防「解析器漏字段静默失效」再犯（SP-2002 走查事故：
 *              classpath 手写解析器漏 requireParam，字段全有默认值导致编译/测试全绿但功能
 *              失效，见 SP-6001）。每个 XML 字段造一条非默认值样本逐一断言，任何一侧
 *              解析器漏接字段都会在此红。
 *              数据用内联 XML 字符串喂 InputStream 入口（曾经的分叉路径），自包含、
 *              不受默认内置配置文件演进影响。
 */
class XmlConfigLoaderTest {

    /** 内联 XML → InputStream（XmlConfigLoader 流入口会自行关流）。 */
    private fun xmlOf(content: String): InputStream = content.byteInputStream()

    // ─────────────────────────────────────────────────────────
    // function_templates.xml
    // ─────────────────────────────────────────────────────────

    @Test
    fun `functionTemplates 全字段非默认值逐一解析`() {
        val xml = """
            <templates>
                <template name="通用应用操作">
                    <!-- state 是 item 元素自身属性（非 attr 子节点），同 function_templates 默认内置文件口径 -->
                    <item state="disable">
                        <attr name="icon">./res/img/app_start_256x256.png</attr>
                        <attr name="text">启动应用</attr>
                        <attr name="cmd">am start -n {0}</attr>
                        <attr name="act">m_start_app</attr>
                        <attr name="shell">false</attr>
                        <attr name="isNeedPkgName">true</attr>
                        <attr name="requireParam">pkg,action</attr>
                        <attr name="isNeedDeviceOnline">true</attr>
                        <attr name="tab">keys</attr>
                        <attr name="size">large</attr>
                        <attr name="desc">拉起指定包名应用</attr>
                        <attr name="accent">rose</attr>
                    </item>
                </template>
            </templates>
        """.trimIndent()

        val templates = XmlConfigLoader.loadFunctionTemplates(xmlOf(xml))

        assertEquals(1, templates.size)
        val item = templates[0].items[0]
        assertEquals("./res/img/app_start_256x256.png", item.icon)
        assertEquals("启动应用", item.text)
        assertEquals("am start -n {0}", item.cmd)
        assertEquals("m_start_app", item.action)
        assertEquals(false, item.shell)
        assertEquals(true, item.needPkgName)
        // SP-2002 事故回归点：requireParam 多字段解析
        assertEquals(listOf("pkg", "action"), item.requireParam)
        assertEquals(true, item.needDeviceOnline)
        assertEquals("disable", item.state)
        assertEquals("keys", item.tab)
        assertEquals("large", item.size)
        assertEquals("拉起指定包名应用", item.desc)
        assertEquals("rose", item.accent)
    }

    @Test
    fun `functionTemplates 缺省字段取默认值`() {
        val xml = """
            <templates>
                <template name="数据获取">
                    <item />
                </template>
            </templates>
        """.trimIndent()

        val templates = XmlConfigLoader.loadFunctionTemplates(xmlOf(xml))

        val item = templates[0].items[0]
        assertEquals("", item.icon)
        assertEquals("", item.cmd)
        assertEquals("", item.action)
        assertEquals(true, item.shell)
        assertEquals(false, item.needPkgName)
        assertEquals(emptyList(), item.requireParam)
        assertEquals(false, item.needDeviceOnline)
        assertEquals("quick", item.tab)
        assertEquals("small", item.size)
    }

    @Test
    fun `functionTemplates template layout 缺省为 grid`() {
        val xml = """
            <templates>
                <template name="数据获取">
                    <item />
                </template>
            </templates>
        """.trimIndent()

        val templates = XmlConfigLoader.loadFunctionTemplates(xmlOf(xml))

        assertEquals("grid", templates[0].layout)
    }

    @Test
    fun `functionTemplates requireParam 空值片段被过滤`() {
        val xml = """
            <templates>
                <template name="通用应用操作">
                    <item>
                        <attr name="requireParam">pkg,,action</attr>
                    </item>
                </template>
            </templates>
        """.trimIndent()

        val templates = XmlConfigLoader.loadFunctionTemplates(xmlOf(xml))

        assertEquals(listOf("pkg", "action"), templates[0].items[0].requireParam)
    }

    // ─────────────────────────────────────────────────────────
    // menus_ui.xml
    // ─────────────────────────────────────────────────────────

    @Test
    fun `menuConfig action 全字段解析含 state`() {
        // menus_ui.xml 的 attr 挂在 action 子节点上（loadMenuConfig 按 menu>action 结构解析）
        val xmlWithAction = """
            <menus>
                <menu name="文件">
                    <!-- state 是 action 元素自身属性（非 attr 子节点），同 menus_ui 默认内置文件口径 -->
                    <action name="新建连接" state="hidden">
                        <attr name="shortcut">Ctrl+N</attr>
                        <attr name="icon">./res/img/new.png</attr>
                        <attr name="action">m_new_connect</attr>
                    </action>
                </menu>
            </menus>
        """.trimIndent()

        val menus = XmlConfigLoader.loadMenuConfig(xmlOf(xmlWithAction))

        assertEquals(1, menus.size)
        val action = menus[0].actions[0]
        assertEquals("新建连接", action.name)
        assertEquals("Ctrl+N", action.shortcut)
        assertEquals("./res/img/new.png", action.icon)
        assertEquals("m_new_connect", action.action)
        // 手写解析器曾有差异的字段（不解析 state）——统一后必须被解析
        assertEquals("hidden", action.state)
    }

    @Test
    fun `menuConfig action 缺 state 为空串`() {
        val xmlWithAction = """
            <menus>
                <menu name="工具">
                    <action name="主题切换" />
                </menu>
            </menus>
        """.trimIndent()

        val menus = XmlConfigLoader.loadMenuConfig(xmlOf(xmlWithAction))

        assertEquals("", menus[0].actions[0].state)
        assertEquals("主题切换", menus[0].actions[0].name)
    }

    // ─────────────────────────────────────────────────────────
    // cmdConfig.xml（保守保留链路，见 SP-6001）
    // ─────────────────────────────────────────────────────────

    @Test
    fun `cmdConfig group item 与 subGroup 嵌套解析`() {
        val xml = """
            <CMD>
                <group name="文件操作">
                    <item name="list_root">ls /root</item>
                    <item name="list_dir" desc="列出目录" shell="false" dst_pkg="true">ls -la {0}</item>
                    <sub_group name="高级">
                        <item name="stat">stat {0}</item>
                    </sub_group>
                </group>
            </CMD>
        """.trimIndent()

        val groups = XmlConfigLoader.loadCmdConfig(xmlOf(xml))

        assertEquals(1, groups.size)
        val group = groups[0]
        assertEquals("文件操作", group.name)
        assertEquals(2, group.items.size)
        assertEquals("ls /root", group.items[0].cmd)
        assertEquals("列出目录", group.items[1].description)
        assertEquals(false, group.items[1].shell)
        assertEquals(true, group.items[1].needDstPkg)
        assertEquals(1, group.subGroups.size)
        assertEquals("高级", group.subGroups[0].name)
        assertEquals("stat {0}", group.subGroups[0].items[0].cmd)
    }

    // ─────────────────────────────────────────────────────────
    // File 入口：文件缺失口径
    // ─────────────────────────────────────────────────────────

    @Test
    fun `file 入口文件缺失返回空列表`() {
        val missing = java.io.File("definitely-not-exist-${System.nanoTime()}.xml")

        assertTrue(XmlConfigLoader.loadCmdConfig(missing).isEmpty())
        assertTrue(XmlConfigLoader.loadFunctionTemplates(missing).isEmpty())
        assertTrue(XmlConfigLoader.loadMenuConfig(missing).isEmpty())
    }
}
