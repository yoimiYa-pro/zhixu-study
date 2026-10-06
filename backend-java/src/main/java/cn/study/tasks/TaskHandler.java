package cn.study.tasks;

import java.util.Map;

public interface TaskHandler {
    String kind();
    Object handle(Map<String,Object> task);
}
