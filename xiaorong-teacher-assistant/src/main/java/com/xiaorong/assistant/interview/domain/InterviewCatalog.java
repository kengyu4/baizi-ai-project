package com.xiaorong.assistant.interview.domain;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A deterministic fallback question bank. The AI is used for scoring and dynamic follow-ups,
 * while this bank keeps interview sessions usable when a provider is unavailable.
 */
@Component
public class InterviewCatalog {
    private static final List<InterviewQuestion> FRONTEND = List.of(
            new InterviewQuestion("请说明 Vue 3 中 ref 与 reactive 的差异，以及各自适合的使用场景。",
                    "说明响应式边界、.value 和解构后的处理方式。",
                    List.of("ref", "reactive", ".value", "toRefs"),
                    "ref 适合基本类型或单值引用，reactive 适合对象；解构 reactive 时需要 toRefs 保持响应式。",
                    "如果把 reactive 对象直接解构，会出现什么问题？你会如何处理？",
                    "请结合一个 composable，说明你如何设计它的响应式返回值。"),
            new InterviewQuestion("你会如何定位并优化一个首屏加载缓慢的前端页面？",
                    "覆盖指标、排查路径和可落地的优化手段。",
                    List.of("LCP", "懒加载", "代码分割", "缓存"),
                    "先使用性能指标定位瓶颈，再通过资源压缩、懒加载、代码分割和缓存验证优化效果。",
                    "当 LCP 偏高时，你会优先检查哪些资源和渲染阶段？",
                    "优化完成后，你会如何避免后续发布造成性能回退？"),
            new InterviewQuestion("请设计一个列表页的请求取消与竞态处理方案。",
                    "说明取消时机、竞态防护和异常状态。",
                    List.of("AbortController", "竞态", "防抖", "loading"),
                    "参数变化时取消旧请求，并以请求序号或 AbortController 避免旧响应覆盖新状态。",
                    "快速切换筛选条件时，旧请求返回更晚会导致什么问题？",
                    "你会如何把这套逻辑封装成可复用的 composable？")
    );

    private static final List<InterviewQuestion> BACKEND = List.of(
            new InterviewQuestion("请说明一次典型的 Java 服务接口如何保证幂等性。",
                    "从业务唯一键、并发控制和重复请求响应说明。",
                    List.of("幂等", "唯一键", "事务", "分布式锁"),
                    "通过请求唯一标识或业务唯一键建立幂等记录，并用事务和必要的锁控制并发。",
                    "仅依赖数据库唯一索引时，接口返回应该如何设计？",
                    "在分布式场景中，幂等记录的过期与重试该如何权衡？"),
            new InterviewQuestion("你会如何排查数据库慢查询，并给出优化方案？",
                    "说明定位、执行计划、索引和验证闭环。",
                    List.of("慢查询日志", "EXPLAIN", "索引", "全表扫描"),
                    "先通过慢查询日志定位 SQL，再用 EXPLAIN 分析执行计划，结合索引和 SQL 改写验证效果。",
                    "EXPLAIN 中出现全表扫描时，你会先判断哪些因素？",
                    "什么时候增加索引反而可能带来负面影响？"),
            new InterviewQuestion("请描述你设计一个高并发下库存扣减接口的思路。",
                    "关注超卖、事务边界、锁与降级。",
                    List.of("库存", "事务", "乐观锁", "超卖"),
                    "用条件更新或乐观锁保证库存不为负，明确事务边界，并为热点场景准备限流或队列化方案。",
                    "条件更新失败时，客户端和服务端分别应如何处理？",
                    "如果某个商品成为热点，你会如何进一步削峰？")
    );

    private static final List<InterviewQuestion> GENERAL = List.of(
            new InterviewQuestion("请介绍一个你主导解决的复杂问题，并说明最终结果。",
                    "使用 STAR 结构说明背景、行动和量化结果。",
                    List.of("背景", "行动", "结果", "复盘"),
                    "清楚交代情境与目标，突出个人行动，并用结果和复盘说明价值。",
                    "在这个项目中，你做过的关键取舍是什么？",
                    "如果重新做一次，你会优先改进哪个环节？"),
            new InterviewQuestion("当你与团队成员对技术方案存在分歧时，会如何推进？",
                    "关注事实依据、协作和决策闭环。",
                    List.of("数据", "方案", "沟通", "决策"),
                    "先澄清目标和约束，再用数据、原型或评审比较方案，并同步记录结论。",
                    "如果负责人最终选择了你不认同的方案，你会怎么做？",
                    "你会用哪些方式验证最终方案确实达成目标？"),
            new InterviewQuestion("请说说你最近学习的一项能力，以及如何将它用于实际工作。",
                    "体现学习路径、实践和沉淀。",
                    List.of("学习", "实践", "验证", "沉淀"),
                    "说明学习动机和路径，用实际任务验证效果，并沉淀为可复用的经验。",
                    "你如何判断这项学习没有停留在表面？",
                    "你会怎样帮助团队成员一起掌握这项能力？")
    );

    public List<InterviewQuestion> questionsFor(String positionName, int count) {
        String position = positionName == null ? "" : positionName.toLowerCase(Locale.ROOT);
        List<InterviewQuestion> source = position.contains("java") || position.contains("backend") || position.contains("后端")
                ? BACKEND : (position.contains("vue") || position.contains("react") || position.contains("frontend") || position.contains("前端")
                ? FRONTEND : GENERAL);
        int target = Math.max(1, Math.min(5, count));
        List<InterviewQuestion> result = new ArrayList<>(target);
        for (int index = 0; index < target; index++) result.add(source.get(index % source.size()));
        return List.copyOf(result);
    }
}
