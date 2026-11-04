/* ============================================================
   ${modelDesc}接口封装。由低代码设计器按模型定义生成，属 B 档：每次发布整文件覆盖。
   要改取数逻辑请回到设计器改模型，或在 src/views/${module}/overrides/ 里覆写区块；
   在这个文件里手改的内容下次发布会被整文件替换掉。

   路径与权限点由模型编码派生，与后端 ${className}Controller 上的 @RequestMapping 一一对应。
   ============================================================ */
<#assign DOLLAR = "$">
import { httpClient } from '@/api/http'
import type { PageResult, QueryParams } from '@yuanda-lowcode-ai/scaffold-ui/types'

/** 列表行：只带勾选进列表的字段。日期是后端格式化好的规范串（yyyy-MM-dd / yyyy-MM-dd HH:mm:ss），前端不再二次转换 */
export interface ${className}Row {
<#list rowFields as f>
  ${f.javaField}: ${f.tsType}<#if f.nullableTs> | null</#if>
</#list>
<#if audit>
  createBy?: string | null
  updateBy?: string | null
  createTime?: string | null
  updateTime?: string | null
</#if>
}

/**
 * 新增与修改的入参：只带勾选了「可录入 / 可编辑」的字段。主键走路径参数，不在这里。
 *
 * <p>数字类字段一律 \`| null\`：Java 侧是 Integer / Long / BigDecimal，本来就能收 null，
 * 必填由后端 Service 校验并回「XX不能为空」—— 前端表单初值是空，类型上先允许 null 才不用骗编译器。
 */
export interface ${className}WriteRequest {
<#list formFields as f>
  /** <#if f.required>必填，由后端 Service 兜底校验<#else>可空</#if> */
  ${f.javaField}: ${f.tsType}<#if f.tsType == 'number' || f.nullableTs> | null</#if>
</#list>
}

export const ${apiVar} = {
  list: (query?: QueryParams) => httpClient.get<PageResult<${className}Row>>('${apiPath}', query),
  /** 返回新建行的主键 */
  create: (body: ${className}WriteRequest) => httpClient.post<number>('${apiPath}', body),
  /** 单行详情：编辑前先取整行，列表行只有勾选进列表的字段 */
  detail: (id: number) => httpClient.get<${className}WriteRequest & { id: number }>(`${apiPath}/${DOLLAR}{id}`),
  /** 整行覆盖可编辑字段：表单提交的是整个表单，后端没勾选的字段一个都不碰 */
  update: (id: number, body: ${className}WriteRequest) => httpClient.put<void>(`${apiPath}/${DOLLAR}{id}`, body),
  remove: (id: number) => httpClient.delete<void>(`${apiPath}/${DOLLAR}{id}`),
  /** 后端生成 CSV，条件与列表同一套 */
  export: (query?: QueryParams) => httpClient.download('${apiPath}/export', query),
}
