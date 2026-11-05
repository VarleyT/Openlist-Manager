package com.bytegrep.oplist_manager.feature.settings

/** 常见 OpenList 设置项 key 的中文名称，未收录的显示原始 key */
private val SETTING_LABELS: Map<String, String> = mapOf(
    // 站点
    "version" to "版本",
    "site_title" to "站点标题",
    "announcement" to "公告",
    "allow_indexed" to "允许被索引",
    "allow_mounted" to "允许挂载",
    "robots_txt" to "Robots.txt",
    "pagination_type" to "分页方式",
    "default_page_size" to "默认分页大小",
    // 样式
    "logo" to "Logo",
    "favicon" to "网站图标",
    "main_color" to "主题色",
    "home_icon" to "首页图标",
    "home_container" to "首页容器",
    "settings_layout" to "设置页布局",
    // 预览
    "text_types" to "文本文件类型",
    "audio_types" to "音频文件类型",
    "video_types" to "视频文件类型",
    "image_types" to "图片文件类型",
    "proxy_types" to "代理文件类型",
    "proxy_ignore_headers" to "代理忽略请求头",
    "external_previews" to "外部预览",
    "iframe_previews" to "Iframe 预览",
    "audio_autoplay" to "音频自动播放",
    "video_autoplay" to "视频自动播放",
    "preview_archives_by_default" to "默认预览压缩包",
    "readme_autorender" to "自动渲染 README",
    "filter_readme_scripts" to "过滤 README 脚本",
    // 全局
    "hide_files" to "隐藏文件",
    "customize_head" to "自定义头部",
    "customize_body" to "自定义内容",
    "link_expiration" to "直链有效期",
    "sign_all" to "全部签名",
    "privacy_regs" to "隐私正则",
    "ocr_api" to "OCR 接口",
    "filename_char_mapping" to "文件名字符映射",
    "forward_direct_link_params" to "转发直链参数",
    "ignore_direct_link_params" to "忽略直链参数",
    "webauthn_login_enabled" to "启用 WebAuthn 登录",
    "share_archive_preview" to "分享压缩包预览",
    "share_force_proxy" to "分享强制代理",
    "share_summary_content" to "分享摘要内容",
    // 离线下载
    "aria2_uri" to "Aria2 地址",
    "aria2_secret" to "Aria2 密钥",
    "qbittorrent_url" to "qBittorrent 地址",
    "qbittorrent_seedtime" to "qBittorrent 做种时间",
    "transmission_uri" to "Transmission 地址",
    "transmission_seedtime" to "Transmission 做种时间",
    "pikpak_temp_dir" to "PikPak 临时目录",
    "thunder_temp_dir" to "迅雷临时目录",
    "115_temp_dir" to "115 临时目录",
    // 索引
    "search_index" to "搜索索引",
    "autoupdate_index" to "自动更新索引",
    "ignore_paths" to "忽略路径",
    "max_index_depth" to "最大索引深度",
    "index_progress" to "索引进度",
    // 单点登录
    "sso_login_enabled" to "启用单点登录",
    "sso_login_platform" to "单点登录平台",
    "sso_client_id" to "客户端 ID",
    "sso_client_secret" to "客户端密钥",
    "sso_organization_name" to "组织名称",
    "sso_application_name" to "应用名称",
    "sso_endpoint_name" to "端点地址",
    "sso_jwt_public_key" to "JWT 公钥",
    "sso_auto_register" to "自动注册",
    "sso_default_dir" to "默认目录",
    "sso_default_permission" to "默认权限",
    "sso_compatibility_mode" to "兼容模式",
    // LDAP
    "ldap_login_enabled" to "启用 LDAP 登录",
    "ldap_server" to "LDAP 服务器",
    "ldap_manager_dn" to "管理员 DN",
    "ldap_manager_password" to "管理员密码",
    "ldap_user_search_base" to "用户搜索基准",
    "ldap_user_search_filter" to "用户搜索过滤器",
    "ldap_default_permission" to "默认权限",
    "ldap_default_dir" to "默认目录",
    "ldap_login_tips" to "登录提示",
    // S3
    "s3_enabled" to "启用 S3",
    "s3_port" to "S3 端口",
    "s3_ssl" to "启用 SSL",
    "s3_access_key_id" to "Access Key ID",
    "s3_secret_access_key" to "Secret Access Key",
    "s3_buckets" to "存储桶",
    // FTP
    "ftp_public_host" to "FTP 公网地址",
    "ftp_pasv_port_map" to "被动模式端口映射",
    "ftp_proxy_user_agent" to "FTP 代理 UA",
    "ftp_mandatory_tls" to "强制 TLS",
    "ftp_implicit_tls" to "隐式 TLS",
    "ftp_tls_private_key_path" to "TLS 私钥路径",
    "ftp_tls_public_cert_path" to "TLS 证书路径",
    "sftp_disable_password_login" to "SFTP 禁用密码登录",
    "sftp_proxy_user_agent" to "SFTP 代理 UA",
    // 流量控制
    "max_server_download_streams" to "服务器下载并发数",
    "max_server_upload_streams" to "服务器上传并发数",
    "max_client_download_streams" to "客户端下载并发数",
    "max_client_upload_streams" to "客户端上传并发数",
    "task_offline_download_thread_num" to "离线下载线程数",
    "task_offline_download_transfer_thread_num" to "离线转存线程数",
    "task_upload_thread_num" to "上传线程数",
    "task_copy_thread_num" to "复制线程数",
    "task_move_thread_num" to "移动线程数",
    "task_decompress_download_thread_num" to "解压下载线程数",
    "task_decompress_upload_thread_num" to "解压上传线程数",
)

/** 下拉类设置的选项中文名：key -> (选项原值 -> 显示名)；品牌名（如 SSO 平台）保持原样 */
private val SETTING_OPTION_LABELS: Map<String, Map<String, String>> = mapOf(
    "pagination_type" to mapOf(
        "all" to "全部",
        "pagination" to "分页",
        "load_more" to "加载更多",
        "auto_load_more" to "自动加载更多",
    ),
    "home_container" to mapOf(
        "max_980px" to "最大 980px",
        "hope_container" to "Hope 容器",
    ),
    "settings_layout" to mapOf(
        "list" to "列表",
        "responsive" to "响应式",
    ),
    "search_index" to mapOf(
        "database" to "数据库",
        "database_non_full_text" to "数据库（非全文）",
        "bleve" to "Bleve",
        "meilisearch" to "Meilisearch",
        "none" to "不启用",
    ),
)

private val SENSITIVE_MARKERS = listOf("token", "secret", "password", "key")

fun settingLabel(key: String): String = SETTING_LABELS[key] ?: key

/** 下拉选项的显示名；未收录时回退原值。提交给服务端的仍是原值 */
fun settingOptionLabel(key: String, option: String): String = SETTING_OPTION_LABELS[key]?.get(option) ?: option

/** key 中包含 token/secret/password/key 视为敏感字段，默认遮罩显示 */
fun isSensitiveSetting(key: String): Boolean {
    val lower = key.lowercase()
    return SENSITIVE_MARKERS.any { lower.contains(it) }
}
