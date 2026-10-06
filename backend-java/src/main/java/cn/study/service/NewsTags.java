package cn.study.service;

import cn.study.dto.NewsModels.Analysis;
import java.util.*;

/** Initial keyword labels remain editable and are refined by the validated AI analysis. */
public final class NewsTags {
    public static final List<String> ALL=List.of("国内时政","国际","经济","科技","教育","民生","法治","生态文明","文化","乡村振兴","基层治理","数字政府");
    private static final List<String> WORDS=List.of("国务院|总书记|习近平|党建|全国人大|政协|中央|国庆","国际|联合国|外交|美国|俄罗斯|日本|欧盟|外国|全球","经济|金融|消费|投资|企业|财政|税收|产业|市场|贸易","科技|技术|创新|航天|卫星|人工智能|机器人|科学","教育|学校|大学|教师|学生|招生|高考","民生|就业|住房|医疗|医保|养老|社保|交通|铁路|健康","法治|法律|司法|法院|检察|执法|公安|犯罪|普法","生态|环保|绿色|低碳|环境|污染|森林|湿地|保护区","文化|文物|非遗|博物馆|电影|图书|文学|艺术","乡村|农业|农村|农民|粮食|丰收","基层|社区|治理|街道|政务服务","数字政府|数字政务|一网通办|数据共享|智慧城市");
    private NewsTags() {}
    public static List<String> infer(String text) {
        var result=new ArrayList<String>();
        for(int i=0;i<ALL.size() && result.size()<6;i++) if(Arrays.stream(WORDS.get(i).split("\\|")).anyMatch(text::contains)) result.add(ALL.get(i));
        return result;
    }
    public static List<String> analysis(Analysis input,List<?> fallback) {
        if(input.tags()!=null && !input.tags().isEmpty()) return input.tags().stream().distinct().limit(6).toList();
        var result=new LinkedHashSet<String>();
        input.examPoints().forEach((key,values)-> { if(!values.isEmpty()) { String tag=switch(key) { case "政治" -> "国内时政"; case "法律" -> "法治"; default -> key; };if(ALL.contains(tag)) result.add(tag); } });
        for(var material:input.materials()) if(ALL.contains(material.category())) result.add(material.category());
        if(result.isEmpty()) for(Object tag:fallback) if(ALL.contains(tag.toString())) result.add(tag.toString());
        return result.stream().limit(6).toList();
    }
}
