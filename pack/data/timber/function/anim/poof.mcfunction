function timber:anim/ctx
scoreboard players operation #h2 timber.data = #h timber.data
scoreboard players operation #h2 timber.data /= #2 timber.data
function timber:anim/walk_poof with storage timber:anim s
execute at @e[type=marker,tag=timber.mid,limit=1] run function timber:anim/fx/poof
execute unless entity @e[type=marker,tag=timber.mid,limit=1] run function timber:anim/fx/poof
function timber:anim/drops
function timber:anim/kill
kill @e[type=marker,tag=timber.pt]
kill @e[type=marker,tag=timber.mid]
kill @s
