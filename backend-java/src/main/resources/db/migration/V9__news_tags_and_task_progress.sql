ALTER TABLE current_affairs ADD COLUMN tags_json jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(tags_json)='array');
ALTER TABLE current_affairs ADD COLUMN tags_manual boolean NOT NULL DEFAULT false;
CREATE INDEX current_affairs_tags_idx ON current_affairs USING gin(tags_json);
UPDATE current_affairs ca SET tags_json=coalesce((
  SELECT jsonb_agg(label ORDER BY position) FROM (
    SELECT label,position FROM (VALUES
      (1,'国内时政','国务院|总书记|习近平|党建|全国人大|政协|中央|国庆'),
      (2,'国际','国际|联合国|外交|美国|俄罗斯|日本|欧盟|外国|全球'),
      (3,'经济','经济|金融|消费|投资|企业|财政|税收|产业|市场|贸易'),
      (4,'科技','科技|技术|创新|航天|卫星|人工智能|机器人|科学'),
      (5,'教育','教育|学校|大学|教师|学生|招生|高考'),
      (6,'民生','民生|就业|住房|医疗|医保|养老|社保|交通|铁路|健康'),
      (7,'法治','法治|法律|司法|法院|检察|执法|公安|犯罪|普法'),
      (8,'生态文明','生态|环保|绿色|低碳|环境|污染|森林|湿地|保护区'),
      (9,'文化','文化|文物|非遗|博物馆|电影|图书|文学|艺术'),
      (10,'乡村振兴','乡村|农业|农村|农民|粮食|丰收'),
      (11,'基层治理','基层|社区|治理|街道|政务服务'),
      (12,'数字政府','数字政府|数字政务|一网通办|数据共享|智慧城市')
    ) AS labels(position,label,keywords)
    WHERE (ca.title || ' ' || left(ca.content,8000)) ~ keywords ORDER BY position LIMIT 6
  ) initial_tags
),'[]'::jsonb);
ALTER TABLE ai_tasks ADD COLUMN progress_done integer NOT NULL DEFAULT 0 CHECK (progress_done>=0);
ALTER TABLE ai_tasks ADD COLUMN progress_total integer CHECK (progress_total>0);
ALTER TABLE ai_tasks ADD COLUMN progress_label varchar(200) NOT NULL DEFAULT '等待处理';
ALTER TABLE ai_tasks ADD CONSTRAINT task_progress_bounds CHECK (progress_total IS NULL OR progress_done<=progress_total);
