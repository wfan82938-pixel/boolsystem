# 基因库科研管理与智能配型系统 (Blood System)

> 免责声明 / Disclaimer  
> 本系统仅作为科研数据管理与辅助检索工具，配型结果仅供参考，严禁直接作为临床输血/移植的唯一依据。最终医疗决策请严格遵循临床检验标准。

本仓库用于血液/基因供者数据的管理与智能配型。后端基于 Spring Boot，前端使用 Thymeleaf + Bootstrap，支持从 Excel 粘贴导入、HLA/HPA 配型、DSA（排除抗体）过滤等。

- 预览页面：登录页、列表页、导入页、配型页均在 `templates/` 中（Thymeleaf）。
- 默认登录账号：`admin` / `123456`

---

## 功能特性

- 供者信息管理：新增、编辑、删除、分页、搜索（按 ID/姓名）
- 批量导入：支持从 Excel 直接复制（Tab 分隔，TSV）粘贴导入
- HPA 基因分型：HPA-1/2/3/4/5/6/10/15/21（aa/ab/bb）
- HLA 高分辨分型（A、B 位点）：自动解析 Group/Code 并入库
- 智能配型：
  - HLA 按 Group 匹配（交叉匹配取最大匹配数）
  - HPA 匹配/相容计分（+5 / +2 / 0）
  - DSA 抗体排除（按 Group 强制排除）
  - 综合评分 + 等级显示（A/B/C/D，禁忌为 X）
- 并发安全：乐观锁（Version 字段）

---

## 仓库结构说明（请注意代码在子目录）

本仓库的 Java 项目位于子目录 `bloodsystem/` 中，根目录仅放文档与脚本。

```
.
├─ README.md                 # 当前说明文档（根目录）
├─ blood_db.sql              # 可选：建库/初始化脚本（根目录）
├─ .gitignore
└─ bloodsystem/              # ← 真正的 Spring Boot 项目在这里
   ├─ pom.xml
   ├─ mvnw / mvnw.cmd        # Maven Wrapper（Windows 用 mvnw.cmd）
   ├─ .mvn/wrapper/*         # Maven Wrapper 配置
   └─ src/
      ├─ main/java/...       # 后端代码
      └─ main/resources/
         ├─ templates/*.html # 前端模板（Thymeleaf）
         ├─ static/css|js    # 静态资源
         └─ application.properties
```

---

## 快速开始

### 1. 环境准备
确保你的本地环境已安装：
- JDK 17
- MySQL 8.0+
- Maven 3.6+（如果不用系统 Maven，则直接使用项目自带的 Maven Wrapper 更简单）

### 2. 数据库配置
1. 在 MySQL 中创建数据库：
   ```sql
   CREATE DATABASE blood_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   ```
2. （可选）执行根目录的 `blood_db.sql` 以初始化（也可直接依赖 JPA 自动建表）。

### 3. 修改配置
编辑：`bloodsystem/src/main/resources/application.properties`

```properties
server.port=8080
spring.datasource.url=jdbc:mysql://localhost:3306/blood_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true
spring.datasource.username=root
# 推荐使用环境变量覆盖密码（默认示例值仅用于本地）：
spring.datasource.password=${DB_PWD:你的数据库密码}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```


### 4. 启动项目
使用IDEA运行:
```
进入项目根目录bloodsystem/src/main/java/com/example/bloodsystem/BloodsystemApplication, 点击右上角的绿色三角运行
```

或者在项目根目录下运行(注意：一定要进入包含 pom.xml 和 mvnw 的那一层目录)：

```bash
mvn clean spring-boot:run
```

或者打包运行：

```bash
mvn clean package
java -jar target/bloodsystem-0.0.1-SNAPSHOT.jar
```

### 5. 访问系统
- 浏览器访问：http://localhost:8080
- 默认账号/密码：`admin` / `123456`  
  （如需修改，请编辑 `SecurityConfig.java` 的内存用户配置）

---

## 使用指南

### A. 批量导入
1. 登录后点击“导入”。
2. 从 Excel 复制数据（建议包含表头），直接粘贴到页面文本框；系统以 Tab 分隔解析（TSV）。
   - 推荐列顺序：`姓名 | ID | HPA-1 | … | HPA-21 | HLA-A1 | HLA-A2 | HLA-B1 | HLA-B2`
   - 空单元格请保留为“空”（不要删除 Tab），系统能正确识别空列。
3. 点击“确认导入”，查看成功/失败详情。

### B. 智能配型
1. 打开“配型查询”页面。
2. 可选条件：
   - 血型（A/B/O/AB）
   - DSA 排除抗体（如：`A*02`、`B*13`；支持回车/逗号/空格分隔；最终按 Group 过滤）
   - HLA（高分辨）：A1/A2、B1/B2，支持输入形式：
     - `HLA-A*02:01`、`A*02:01`、`02:01`、`02`（系统会自动解析 Group）
   - HPA：选择各位点的 aa/ab/bb
3. 点击“立即配型”查看结果列表：
   - 绿色：匹配
   - 蓝色：相容
   - 橙色：部分匹配较低
   - 红色：禁忌（被抗体排除）
4. 评分说明（简要）：
   - HLA：仅按 Group 匹配；每条链匹配 +100 分；交叉匹配取最大匹配数；0~4 条匹配对应 D/C/B/A 等级
   - HPA：匹配 +5，相容 +2，不匹配 0；显示匹配率进度条
   - DSA：命中任一被排除的 Group，直接 -1000 分（显示禁忌与原因）

---

## 默认账号

- 登录页面路径：`/login`
- Spring Security 已启用，默认使用内存账号：
  ```java
  username: admin
  password: 123456
  role: ADMIN
  ```