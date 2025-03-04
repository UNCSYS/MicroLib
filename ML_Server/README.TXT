use UTF-8! 请使用UTF-8
采用中英双语文件 ZH-rCN & EN
本源码主要使用java编译
 1.下载源码第一步 运行init.bat文件 生成必要的证书 处理一些事务
 2.在SERVER_Main.java中 配置数据 当然 几乎所有内容都已经配置完成 你需要配置的内容如下
 2-1 在SERVER_Main这个类中createSSLServerSocket方法 配置其中的keyPassword为你刚才生成证书的密码
 3.如果你使用了VScode 可以直接运行 如果是jdk(建议jdk20-21) 可以使用java SERVER_Main.java运行服务器
END 很好 你已经学会了这个轻量服务器的使用了
 如果你没有更改过多内容 127.0.0.1/HTML/将会被映射为 data/HTML/下的文件
 现在 尝试使用127.0.0.1/HTML/index/indexA.html来访问主页吧
