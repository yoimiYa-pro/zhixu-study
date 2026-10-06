package cn.study.exception;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log=LoggerFactory.getLogger(ApiErrors.class);
    private ResponseEntity<?> error(HttpStatusCode status,String code,String message,HttpServletRequest req) {
        String id=UUID.randomUUID().toString();
        if(status.is5xxServerError()) log.error("request={} code={} method={}",id,code,req.getMethod());
        else log.warn("request={} code={} status={}",id,code,status.value());
        return ResponseEntity.status(status).header("X-Request-ID",id).body(Map.of("code",code,"message",message,"requestId",id));
    }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e,HttpServletRequest r) {
        return error(e.getStatusCode(),"HTTP_"+e.getStatusCode().value(),Objects.requireNonNullElse(e.getReason(),"请求失败"),r);
    }
    @ExceptionHandler(cn.study.service.AiClient.Failure.class) ResponseEntity<?> ai(cn.study.service.AiClient.Failure e,HttpServletRequest r) {
        String message=switch(e.code) { case "LLM_NOT_CONFIGURED"->"请在模型管理中添加接入，或配置服务器默认模型";case "EMBEDDING_NOT_CONFIGURED"->"请先配置向量模型，再建立知识索引";case "AI_INVALID_OUTPUT","AI_INVALID_JSON"->"模型输出未通过校验，请重新尝试";case "AI_MODELS_UNAVAILABLE"->"暂时无法获取可用模型，请稍后重试";case "AI_MODEL_INVALID"->"模型配置无效，请检查地址与模型名称";case "AI_MODEL_UNAVAILABLE"->"模型未通过连接与结构化输出测试，请检查配置";case "PROVIDER_UNAVAILABLE"->"连接失败，请检查 Base URL、API Key 与模型名称；接口不支持 JSON 模式时可关闭该选项";case "AI_CREDENTIALS_UNAVAILABLE"->"模型密钥无法读取，请编辑接入并重新填写 API Key";case "AI_URL_REJECTED"->"Base URL 需要使用可访问的 HTTPS 公网接口";default->"AI 增强功能暂时不可用，可检查配置或重试"; };
        return error(HttpStatus.SERVICE_UNAVAILABLE,e.code,message,r);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e,HttpServletRequest r) {
        return error(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","请检查必填项、字段长度和数值范围",r);
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) ResponseEntity<?> uploadSize(Exception e,HttpServletRequest r) { return error(HttpStatus.PAYLOAD_TOO_LARGE,"UPLOAD_TOO_LARGE","上传文件过大，请选择较小的图片",r); }
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class) ResponseEntity<?> missingFile(Exception e,HttpServletRequest r) { return error(HttpStatus.BAD_REQUEST,"FILE_REQUIRED","请选择要上传的图片",r); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception e,HttpServletRequest r) { return error(HttpStatus.CONFLICT,"DATA_CONFLICT","记录重复或仍被其他记录引用",r); }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> database(Exception e,HttpServletRequest r) { return error(HttpStatus.SERVICE_UNAVAILABLE,"DATABASE_UNAVAILABLE","数据库暂时不可用，请稍后重试",r); }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,jakarta.validation.ConstraintViolationException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> bad(Exception e,HttpServletRequest r) { return error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","输入格式不正确",r); }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e,HttpServletRequest r) { return error(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","服务处理失败，请使用请求编号排查",r); }
}
