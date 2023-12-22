PushButton = """
# /*这里是通用设置，所有按钮都有效，不过后面的可以覆盖这个*/
# QPushButton {
#     border: none; /*去掉边框*/
# }

# /*
# QPushButton#xxx
# 或者
# #xx
# 都表示通过设置的objectName来指定
# */
QPushButton#ToolButton {
    background-color: #00c3c3c3; /*背景颜色*/
    # border:1px solid #303030;
}
ToolButton:hover {
    background-color: #ff0909; /*鼠标悬停时背景颜色*/
}
# /*注意pressed一定要放在hover的后面，否则没有效果*/
#ToolButton:pressed {
    background-color: #ffcdd2; /*鼠标按下不放时背景颜色*/
}

#BlueButton {
    background-color: #2196f3;
    padding-top:8px; //文字向下移动
    text-align:left; 文字左对齐
    image:url(":/delete.png"); //加图标
    /*限制最小最大尺寸*/
    min-width: 96px;
    max-width: 96px;
    min-height: 96px;
    max-height: 96px;
    border-radius: 48px; /*圆形*/
    border-top-right-radius: 20px; /*右上角圆角*/
    border-bottom-left-radius: 20px; /*左下角圆角*/
}

#QPushButton:disabled { /*设置禁用时按钮的样式*/ }

/*根据文字内容来区分按钮,同理还可以根据其它属性来区分*/
QPushButton[text="purple button"] {
    color: white; /*文字颜色*/
    background-color: #9c27b0;
}


"""