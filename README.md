## 必须遵守以下约定：
- 完成一个phrase进行一次commit
- 所有删除的文件移动到目录下的trash文件夹
- 写一个Github Actions，当push到main分支时，自动构建apk文件，当版本更新时，自动发布release，更新版本号，写一个CHANGELOG.md
- 最终apk文件要界面简洁美观，获取尽量少的权限来实现目标结果，最终apk大小尽量小

## 功能
- 界面一进去要求用户输入卡号和密码，并保存在本地，不上传到任何地方。一次输入永久保存，除非用户手动删除
- 自动连接校园网 Wi-Fi：优先宿舍区 `SZU_CTC&CMCC`，失败再试教学区 `SZU_WLAN` / `SZU-WLAN`
- 按区域识别宿舍区 / 教学区（SSID → IP 网段 → 认证服务器探测），走对应认证协议：
  - 宿舍区：Dr.COM eportal `http://172.30.255.42:801/eportal/portal/login`（`user_account=,0,<账号>` 及完整查询参数/请求头），以响应体 `result:1` / 认证成功文案判定，再访问 baidu.com 确认上网
  - 教学区：深澜 Srun（`https://net.szu.edu.cn`）challenge → HMAC-MD5 → XXTEA → 自定义 base64 → SHA1，动态抓取 `ac_id`；直连失败时从网关 302 探测可用门户入口
- 若连接成功，返回正常，则尝试访问baidu.com确认是否可以正常上网，可以正常上网则跳出弹窗显示"我在深大联网仅用xx次就联网成功，你也快来试试吧"，同时将数字加粗加大显示，整个弹窗版面突出数字。此时计数器归零，日志清零
- 若连接失败，则断开网络，等待3s后重新连接网络进行上述流程，同时计数器加1
- 程序界面下方显示日志

## 原理参考
双区认证流程对齐 [szu-net-autologin-mac](https://github.com/JennieYow/szu-net-autologin-mac)（宿舍 eportal + 教学区深澜 Srun）。
