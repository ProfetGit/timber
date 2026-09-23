execute unless data storage timber:anim s.drops[0] run return 0
data modify storage timber:anim it set value {Age:1s,PickupDelay:10s,Motion:[0.0d,0.0d,0.0d]}
data modify storage timber:anim it.Item set from storage timber:anim s.drops[0]
execute if entity @a[tag=timber.owner] run data modify storage timber:anim it.PickupDelay set value 0s
execute unless entity @a[tag=timber.owner] store result storage timber:anim it.Motion[0] double 0.001 run random value -140..140
execute unless entity @a[tag=timber.owner] store result storage timber:anim it.Motion[1] double 0.001 run random value 180..320
execute unless entity @a[tag=timber.owner] store result storage timber:anim it.Motion[2] double 0.001 run random value -140..140
execute if entity @a[tag=timber.owner] at @a[tag=timber.owner,limit=1] run function timber:anim/drop_spawn with storage timber:anim
execute unless entity @a[tag=timber.owner] at @e[type=marker,tag=timber.pt,sort=random,limit=1] run function timber:anim/drop_spawn with storage timber:anim
data remove storage timber:anim s.drops[0]
function timber:anim/drop_one
