package top.panll.assist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.panll.assist.controller.bean.ControllerException;
import top.panll.assist.controller.bean.ErrorCode;
import top.panll.assist.controller.bean.WVPResult;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final static Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 默认异常处理
     * @param e 异常
     * @return 统一返回结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public WVPResult<String> exceptionHandler(Exception e) {
        logger.error("[全局异常]： ", e);
        return WVPResult.fail(ErrorCode.ERROR500.getCode(), e.getMessage());
    }

    /**
     * 自定义异常处理， 处理controller中返回的错误
     * @param e 异常
     * @return 统一返回结果
     */
    @ExceptionHandler(ControllerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public WVPResult<String> exceptionHandler(ControllerException e) {
        return WVPResult.fail(e.getCode(), e.getMsg());
    }

}
