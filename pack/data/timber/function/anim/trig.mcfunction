# #th (radians x10000) -> #sn #cs (its sine and cosine) and #hs #hc (of the half angle, for the quaternion), x10000
scoreboard players operation #t1 timber.data = #th timber.data
function timber:anim/sincos
scoreboard players operation #sn timber.data = #s1 timber.data
scoreboard players operation #cs timber.data = #c1 timber.data
scoreboard players operation #t1 timber.data = #th timber.data
scoreboard players operation #t1 timber.data /= #2 timber.data
function timber:anim/sincos
scoreboard players operation #hs timber.data = #s1 timber.data
scoreboard players operation #hc timber.data = #c1 timber.data
