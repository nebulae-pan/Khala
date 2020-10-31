## 组件化编译插件

### 支持功能
- 组件指定接口暴露
- application module输出aar
- 简单路由（参数装配，路由拦截）
- aar包自动增加前缀
- 组件间初始化按依赖执行
- apk资源文件去重
- 依赖开发期隔离

### demo工程使用
#### 构建
在terminal内执行如下指令
```
./gradlew Khala-base:uploadArchives && ./gradlew Khala-plugin:uploadArchives
```
执行完毕后，插件会部署到项目下的repo目录中，此时重新sync工程即可

#### 上传插件
cpnt-test是一个简单的组件化模块展示的module（有和其他module同名的资源），terminal中执行`./gradlew cpnt-test:uploadComponent`，会将该module的aar包部署到项目下的repo目录中。
部署完成后，运行app，即可看到效果。

### 新组件
创建新的application工程，使用`apply plugin:'io.nebula.khala'`替换`apply plugin:'com.android.application'`。
需要aar包时，terminal中运行 `./gradlew [module-name]:uploadComponent`，便会通过maven插件部署aar包。
如果需要对外暴露接口，有两种方式。
- 创建一个接口类继承`IComponentService`，该类所有引用到的类都会对外暴露。
- 为需要对外暴露的类添加注解@ClassExposed

### 使用组件
在build.gradle中添加如下配置
```
component {
    dependencies {
        // 下面两种依赖方法视情况选择其一，如果组件不需要任何对外暴露接口，使用implementation，否则选择interfaceApi
        implementation '[group]:[artifactId]:[version]'
        // interfaceApi '[group]:[artifactId]:[version]'
    }
}
```
使用implementation依赖的组件，无法在开发时直接引用到任何代码。可以通过路由进行跳转

### 不足点
- 插件基于agp 3.5.x开发，由于agp兼容性较差，工程接入需要对齐agp的开销
- 只能为打包为aar的module增加资源前缀，aar中只包含当前module中的代码，其依赖的lib没有打包到aar中，因此放在lib中的资源不会添加前缀