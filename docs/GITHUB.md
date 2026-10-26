# GitHub 发布与更新检查

## 1. 创建远程仓库

当前项目已经初始化为本地 Git 仓库。请在 GitHub 创建一个空仓库，然后执行：

```bash
cd zhizhang
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

不要提交：

- `keystore/`
- `keystore.properties`
- `local.properties`
- `dist/`
- `app/build/`

这些已经在 `.gitignore` 中排除。

## 2. 发布一个 GitHub Release

1. 在 GitHub 仓库进入 Releases → Draft a new release。
2. Tag 使用版本号，例如 `v1.0.1`。
3. 上传已签名的 APK，例如：
   `zhizhang-1.0.1-release.apk`
4. Release 说明里写更新内容。
5. 发布。

检查更新会读取：

```text
https://api.github.com/repos/<owner>/<repo>/releases/latest
```

因此仓库需要是公开仓库，Release 必须附带 `.apk` 资产。

## 3. Release 签名

仓库不保存签名文件。签名文件在本机：

- `keystore/zhizhang-release.jks`
- `keystore.properties`

它们必须单独备份。以后所有版本必须使用同一个 keystore，否则不能覆盖升级。

## 4. 更新检查需要的仓库信息

需要确认：

- GitHub 用户名 / 组织名
- 仓库名
- 仓库是否公开
- Release 资产是否使用 `.apk`

确认后会把仓库地址写入 App 的更新检查配置。
