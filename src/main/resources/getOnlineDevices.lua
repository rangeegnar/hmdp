-- getOnlineDevices.lua
-- 功能：查询用户在线设备列表，并原子清理已失效的僵尸 token
--       （将原 Java 侧「ZRANGE + 逐个 hasKey + ZREM 惰性清理」压缩为一次往返 + 原子操作）
-- KEYS[1] : login:user:tokens:{userId}（设备登记 ZSet）
-- ARGV[1] : login:token:（token 详情 key 前缀）
-- 返回   : JSON 字符串，结构如下：
--          {"deviceList":[["<token>",<loginTime>],...],"expiredTokens":["<token>",...]}

-- 1. 取出该用户的全部设备 token 及登录时间戳
--    （WITHSCORES 返回扁平数组：t1,s1,t2,s2,...）
local members = redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES')
if #members == 0 then
    return '{"deviceList":[],"expiredTokens":[]}'
end

-- 2. 批量判断存活：EXISTS 只返回一个总数，无法区分每个 key，
--    故改用 MGET —— 始终返回数组，key 不存在时对应位为 nil
local tokenKeys = {}
for i = 1, #members, 2 do
    tokenKeys[#tokenKeys + 1] = ARGV[1] .. members[i]
end
local aliveVals = redis.call('MGET', unpack(tokenKeys))

-- 3. 按存活标识分组：存活进 deviceList，失效进 expiredTokens
local deviceList = {}
local expiredTokens = {}
for i = 1, #aliveVals do
    local member = members[(i - 1) * 2 + 1]
    local score = members[(i - 1) * 2 + 2]
    if aliveVals[i] ~= nil then
        deviceList[#deviceList + 1] = { member, tonumber(score) }
    else
        expiredTokens[#expiredTokens + 1] = member
    end
end

-- 4. 原子清理僵尸 token（空表时 unpack 会报错，必须先判空）
if #expiredTokens > 0 then
    redis.call('ZREM', KEYS[1], unpack(expiredTokens))
end

-- 5. 空数组强制编码为 []，避免 Java 端把空对象 {} 误解析为设备
if #deviceList == 0 then
    deviceList = cjson.empty_array
end
if #expiredTokens == 0 then
    expiredTokens = cjson.empty_array
end

return cjson.encode({ deviceList = deviceList, expiredTokens = expiredTokens })