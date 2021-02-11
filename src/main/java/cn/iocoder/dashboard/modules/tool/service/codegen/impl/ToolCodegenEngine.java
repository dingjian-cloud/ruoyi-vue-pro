package cn.iocoder.dashboard.modules.tool.service.codegen.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import cn.iocoder.dashboard.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.dashboard.common.pojo.CommonResult;
import cn.iocoder.dashboard.common.pojo.PageParam;
import cn.iocoder.dashboard.common.pojo.PageResult;
import cn.iocoder.dashboard.framework.codegen.config.CodegenProperties;
import cn.iocoder.dashboard.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.dashboard.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.dashboard.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.dashboard.modules.tool.dal.dataobject.codegen.ToolCodegenColumnDO;
import cn.iocoder.dashboard.modules.tool.dal.dataobject.codegen.ToolCodegenTableDO;
import cn.iocoder.dashboard.util.collection.CollectionUtils;
import cn.iocoder.dashboard.util.date.DateUtils;
import com.google.common.collect.Maps;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.hutool.core.text.CharSequenceUtil.*;

/**
 * 代码生成的引擎，用于具体生成代码
 * 目前基于 {@link org.apache.velocity.app.Velocity} 模板引擎实现
 *
 * 考虑到 Java 模板引擎的框架非常多，Freemarker、Velocity、Thymeleaf 等等，所以我们采用 hutool 封装的 {@link cn.hutool.extra.template.Template} 抽象
 *
 * @author 芋道源码
 */
@Component
public class ToolCodegenEngine {

    /**
     * 模板配置
     * key：模板在 resources 的地址
     * value：生成的路径
     */
    private static final Map<String, String> TEMPLATES = MapUtil.<String, String>builder(new LinkedHashMap<>()) // 有序
            // Java
            .put(javaTemplatePath("controller/controller"),
                    javaFilePath("controller/${table.businessName}/${table.className}Controller"))
            .put(javaTemplatePath("controller/vo/baseVO"),
                    javaFilePath("controller/${table.businessName}/vo/${table.className}BaseVO"))
            .put(javaTemplatePath("controller/vo/createReqVO"),
                    javaFilePath("controller/${table.businessName}/vo/${table.className}CreateReqVO"))
            .put(javaTemplatePath("controller/vo/pageReqVO"),
                    javaFilePath("controller/${table.businessName}/vo/${table.className}PageReqVO"))
            .put(javaTemplatePath("controller/vo/respVO"),
                    javaFilePath("controller/${table.businessName}/vo/${table.className}RespVO"))
            .put(javaTemplatePath("controller/vo/updateReqVO"),
                    javaFilePath("controller/${table.businessName}/vo/${table.className}UpdateReqVO"))
            .put(javaTemplatePath("convert/convert"),
                    javaFilePath("convert/${table.businessName}/${table.className}Convert"))
            .put(javaTemplatePath("dal/do"),
                    javaFilePath("dal/dataobject/${table.businessName}/${table.className}DO"))
            .put(javaTemplatePath("dal/mapper"),
                    javaFilePath("dal/mysql/${table.businessName}/${table.className}Mapper"))
            .put(javaTemplatePath("enums/errorcode"),
                    javaFilePath("enums/${simpleModuleName_upperFirst}ErrorCodeConstants"))
            .put(javaTemplatePath("service/service"),
                    javaFilePath("service/${table.businessName}/${table.className}Service"))
            .put(javaTemplatePath("service/serviceImpl"),
                    javaFilePath("service/${table.businessName}/impl/${table.className}ServiceImpl"))
            // Vue
            .put(vueTemplatePath("views/index.vue"),
                    vueFilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            // SQL
            .build();

    @Resource
    private ToolCodegenBuilder codegenBuilder;

    @Resource
    private CodegenProperties codegenProperties;

    /**
     * 模板引擎，由 hutool 实现
     */
    private final TemplateEngine templateEngine;
    /**
     * 全局通用变量映射
     */
    private final Map<String, Object> globalBindingMap = new HashMap<>();

    public ToolCodegenEngine() {
        // 初始化 TemplateEngine 属性
        TemplateConfig config = new TemplateConfig();
        config.setResourceMode(TemplateConfig.ResourceMode.CLASSPATH);
        this.templateEngine = TemplateUtil.createEngine(config);
    }

    @PostConstruct
    private void initGlobalBindingMap() {
        // 全局配置
        globalBindingMap.put("basePackage", codegenProperties.getBasePackage());
        // 全局 Java Bean
        globalBindingMap.put("CommonResultClassName", CommonResult.class.getName());
        globalBindingMap.put("PageResultClassName", PageResult.class.getName());
        globalBindingMap.put("DateUtilsClassName", DateUtils.class.getName());
        globalBindingMap.put("ServiceExceptionUtilClassName", ServiceExceptionUtil.class.getName());
        // VO 类，独有字段
        globalBindingMap.put("PageParamClassName", PageParam.class.getName());
        // DO 类，独有字段
        globalBindingMap.put("baseDOFields", ToolCodegenBuilder.BASE_DO_FIELDS);
        globalBindingMap.put("BaseDOClassName", BaseDO.class.getName());
        globalBindingMap.put("QueryWrapperClassName", QueryWrapperX.class.getName());
        globalBindingMap.put("BaseMapperClassName", BaseMapperX.class.getName());
    }

    public Map<String, String> execute(ToolCodegenTableDO table, List<ToolCodegenColumnDO> columns) {
        // 创建 bindingMap
        Map<String, Object> bindingMap = new HashMap<>(globalBindingMap);
        bindingMap.put("table", table);
        bindingMap.put("columns", columns);
        bindingMap.put("primaryColumn", CollectionUtils.findFirst(columns, ToolCodegenColumnDO::getPrimaryKey)); // 主键字段
        // moduleName 相关
        String simpleModuleName = codegenBuilder.getSimpleModuleName(table.getModuleName());
        bindingMap.put("simpleModuleName", simpleModuleName); // 将 system 转成 sys
        bindingMap.put("simpleModuleName_upperFirst", upperFirst(simpleModuleName)); // 将 sys 转成 Sys
        // className 相关
        String simpleClassName = subAfter(table.getClassName(), upperFirst(simpleModuleName)
                , false); // 将 TestDictType 转换成 DictType. 因为在 create 等方法后，不需要带上 Test 前缀
        bindingMap.put("simpleClassName", simpleClassName);
        bindingMap.put("simpleClassName_underlineCase", toUnderlineCase(simpleClassName)); // 将 DictType 转换成 dict_type
        bindingMap.put("classNameVar", lowerFirst(simpleClassName)); // 将 DictType 转换成 dictType，用于变量
        bindingMap.put("simpleClassName_strikeCase", toSymbolCase(simpleClassName, '-')); // 将 DictType 转换成 dict-type

        // 执行生成
        final Map<String, String> result = Maps.newLinkedHashMapWithExpectedSize(TEMPLATES.size()); // 有序
        TEMPLATES.forEach((vmPath, filePath) -> {
            filePath = formatFilePath(filePath, bindingMap);
            String content = templateEngine.getTemplate(vmPath).render(bindingMap);
            result.put(filePath, content);
        });
        return result;
    }

    private String formatFilePath(String filePath, Map<String, Object> bindingMap) {
        filePath = StrUtil.replace(filePath, "${basePackage}", ((String) bindingMap.get("basePackage")).replaceAll("\\.", "/"));
        ToolCodegenTableDO table = (ToolCodegenTableDO) bindingMap.get("table");
        filePath = StrUtil.replace(filePath, "${simpleModuleName_upperFirst}", (String) bindingMap.get("simpleModuleName_upperFirst"));
        filePath = StrUtil.replace(filePath, "${table.moduleName}", table.getModuleName());
        filePath = StrUtil.replace(filePath, "${table.businessName}", table.getBusinessName());
        filePath = StrUtil.replace(filePath, "${table.className}", table.getClassName());
        return filePath;
    }

    private static String javaTemplatePath(String path) {
        return "codegen/java/" + path + ".vm";
    }

    private static String javaFilePath(String path) {
        return "java/${basePackage}/${table.moduleName}/" + path + ".java";
    }

    private static String vueTemplatePath(String path) {
        return "codegen/vue/" + path + ".vm";
    }

    private static String vueFilePath(String path) {
        return "vue/" + path;
    }

}
