#!lua name=testlib

-- Delete a key if its value is equal to the given value
local function del_ifeq(keys, args)
    local key = keys[1]
    local value = args[1]

    if redis.call("get", key) == value
    then
        return redis.call("del", key)
    else
        return 0
    end
end

redis.register_function('del_ifeq', del_ifeq)
