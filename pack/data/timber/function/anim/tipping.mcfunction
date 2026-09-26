execute if score @s timber.t < @s timber.dur run return 0
execute if score @s timber.dr matches 1.. unless entity @s[tag=timber.dropped] run return run function timber:anim/drop_start
function timber:anim/impact
