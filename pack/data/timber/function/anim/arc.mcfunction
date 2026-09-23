scoreboard players operation #s timber.data = @s timber.t
$scoreboard players remove #s timber.data $(t0)
$scoreboard players set #q timber.data $(len)
scoreboard players operation #q timber.data -= #s timber.data
scoreboard players operation #s timber.data *= #q timber.data
scoreboard players operation #s timber.data *= @s timber.b
$scoreboard players set #q timber.data $(num)
scoreboard players operation #s timber.data *= #q timber.data
$scoreboard players set #q timber.data $(den)
scoreboard players operation #s timber.data /= #q timber.data
scoreboard players operation @s timber.p = @s timber.hit
scoreboard players operation @s timber.p -= @s timber.e
scoreboard players operation @s timber.p -= #s timber.data
