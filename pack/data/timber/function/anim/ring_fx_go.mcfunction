execute if score @s timber.kf > @s timber.km run return 0
execute if score @s timber.t < @s timber.kx run return 0
function timber:anim/ring_fx
scoreboard players add @s timber.kf 1
scoreboard players operation #g timber.data = @s timber.kf
function timber:anim/gap
scoreboard players operation @s timber.kx += #g timber.data
function timber:anim/ring_fx_go
