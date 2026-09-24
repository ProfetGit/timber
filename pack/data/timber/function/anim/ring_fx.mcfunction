function timber:anim/ctx
scoreboard players operation #k timber.data = @s timber.kf
scoreboard players set #any timber.data 0
execute if function timber:anim/ring_any run scoreboard players set #any timber.data 1
scoreboard players operation #a timber.data = #k timber.data
scoreboard players operation #a timber.data *= #1000 timber.data
scoreboard players add #a timber.data 500
execute store result storage timber:anim s.kk double 0.001 run scoreboard players get #a timber.data
execute store result score #a timber.data run data get storage timber:anim s.ky
scoreboard players operation #a timber.data += #k timber.data
execute store result storage timber:anim s.ry int 1 run scoreboard players get #a timber.data
scoreboard players operation #a timber.data = #k timber.data
scoreboard players operation #a timber.data *= #6 timber.data
scoreboard players add #a timber.data 80
execute if score #a timber.data matches 171.. run scoreboard players set #a timber.data 170
execute store result storage timber:anim s.pp double 0.01 run scoreboard players get #a timber.data
scoreboard players add #a timber.data 30
execute store result storage timber:anim s.pq double 0.01 run scoreboard players get #a timber.data
function timber:anim/ring_at with storage timber:anim s
data modify entity @s data.drops set from storage timber:anim s.drops
