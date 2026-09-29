-- loginTokenKick.lua
-- 功能：登录时原子完成「超量踢出最早设备 + 登记新 Token + 设备集合兜底过期」
--       （修复原 Java 侧 zCard 检查 -> range 取最旧 -> delete 三步非原子导致的并发竞态：
--         同一账号并发登录可能同时通过数量检查，最终设备数超过上限）
-- KEYS[1] : login:user:tokens:{userId}（设备登记 ZSet）
-- ARGV[1] : 设备上限（如 3）
-- ARGV[2] : 新 Token
-- ARGV[3] : login:token:（token 详情 key 前缀，拼接被踢设备的 key 后 DEL）
-- ARGV[4] : 当前时间戳（作为新 Token 的 ZSet score，即登录时间）
-- ARGV[5] : 设备集合 TTL（秒）
-- 返回   : 被踢掉的旧 Token 列表，',' 分隔；未踢出时返回空串

local limit = tonumber(ARGV[1])
local kicked = {}

-- 只要当前设备数达到上限，就持续剔除最早登录的设备（Score 最小）
local count = redis.call('ZCARD', KEYS[1])
while count >= limit do
    local oldest = redis.call('ZRANGE', KEYS[1], 0, 0)
    if #oldest == 0 then
        break
    end
    local oldestToken = oldest[1]
    -- 销毁旧设备登录态，并从设备集合中移除
    redis.call('DEL', ARGV[3] .. oldestToken)
    redis.call('ZREM', KEYS[1], oldestToken)
    table.insert(kicked, oldestToken)
    count = count - 1
end

-- 登记当前设备（Score 为当前登录时间戳）
redis.call('ZADD', KEYS[1], ARGV[4], ARGV[2])
-- 设备集合兜底过期时间
redis.call('EXPIRE', KEYS[1], ARGV[5])

-- 返回被踢 Token（',' 连接），未踢出为空串
return table.concat(kicked, ',')