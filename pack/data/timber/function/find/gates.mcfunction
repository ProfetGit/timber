execute if score @s timber.off matches 1 run return fail
execute unless entity @s[gamemode=survival] unless entity @s[gamemode=adventure] run return fail
execute if score #sneak timber.config matches 0 if predicate timber:is_sneaking run return fail
execute if score #sneak timber.config matches 1 unless predicate timber:is_sneaking run return fail
execute if score #require_axe timber.config matches 1 unless items entity @s weapon.mainhand #minecraft:axes run return fail
return 1
