package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class DownloadTokenService
{
    private static final String DOWNLOAD_TOKEN_CACHE_PREFIX = "data:bussiness:download:url:";
    private static final int DOWNLOAD_TOKEN_EXPIRE_MINUTES = 1;

    @Autowired
    private RedisCache redisCache;

    public String createToken(DdataInfo ddataInfo)
    {
        if (ddataInfo == null || ddataInfo.getId() == null)
        {
            throw new ServiceException("下载参数不能为空");
        }

        DdataInfo cacheData = new DdataInfo();
        cacheData.setId(ddataInfo.getId());

        String token = UUID.randomUUID().toString().replace("-", "");
        redisCache.setCacheObject(
                DOWNLOAD_TOKEN_CACHE_PREFIX + token,
                cacheData,
                DOWNLOAD_TOKEN_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );
        return token;
    }

    public DdataInfo validateToken(String token){
        if (StringUtils.isEmpty(token))
        {
            throw new ServiceException("下载 token 不能为空");
        }

        String cacheKey = DOWNLOAD_TOKEN_CACHE_PREFIX + token;
        DdataInfo ddataInfo = redisCache.getCacheObject(cacheKey);
        if (ddataInfo == null || ddataInfo.getId() == null)
        {
            throw new ServiceException("下载链接已失效，请重新下载");
        }
        return ddataInfo;
    }

    public DdataInfo parseToken(String token)
    {
        if (StringUtils.isEmpty(token))
        {
            throw new ServiceException("下载 token 不能为空");
        }

        String cacheKey = DOWNLOAD_TOKEN_CACHE_PREFIX + token;
        DdataInfo ddataInfo = redisCache.getCacheObject(cacheKey);
        if (ddataInfo == null || ddataInfo.getId() == null)
        {
            throw new ServiceException("下载链接已失效，请重新下载");
        }

        redisCache.deleteObject(cacheKey);
        return ddataInfo;
    }
}
