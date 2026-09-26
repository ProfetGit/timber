# the next ring starts one (squashed) block further along the trunk, and slides forward by the bend at this ring:
# bend (k/h)^2 radians of the tip bend, as a sideways step of that many blocks
scoreboard players operation #a timber.data = #cs timber.data
scoreboard players operation #a timber.data *= #gy timber.data
scoreboard players operation #a timber.data /= #10000 timber.data
scoreboard players operation #oy timber.data += #a timber.data
scoreboard players operation #a timber.data = #sn timber.data
scoreboard players operation #a timber.data *= #gy timber.data
scoreboard players operation #a timber.data /= #10000 timber.data
scoreboard players operation #oz timber.data += #a timber.data
execute if score #br timber.data matches 0 run return 0
function timber:anim/xf_w
scoreboard players operation #d timber.data = #br timber.data
scoreboard players operation #d timber.data *= #w timber.data
scoreboard players operation #d timber.data /= #100000 timber.data
scoreboard players operation #a timber.data = #d timber.data
scoreboard players operation #a timber.data *= #sn timber.data
scoreboard players operation #a timber.data /= #10000 timber.data
scoreboard players operation #oy timber.data -= #a timber.data
scoreboard players operation #a timber.data = #d timber.data
scoreboard players operation #a timber.data *= #cs timber.data
scoreboard players operation #a timber.data /= #10000 timber.data
scoreboard players operation #oz timber.data += #a timber.data
