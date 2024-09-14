import configparser


class AppConfigManager:
    """
    应用配置管理器
    """

    _instance = None  # 单例实例

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(AppConfigManager, cls).__new__(cls)
            cls._instance.config = configparser.ConfigParser()
            cls._instance.load_config(*args, **kwargs)
        return cls._instance

    def load_config(self, path):
        self.config.read(path, encoding='utf-8')

    def permissions(self, key):
        try:
            return self.config.get('AndroidPermissions', key)
        except (configparser.NoOptionError, configparser.NoSectionError):
            return key  # 如果找不到key，则返回原值
