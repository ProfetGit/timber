execute unless data storage timber:meta version run return 0
execute unless score #stage timber.data matches 5 run return 0
scoreboard players set #stage timber.data 6
scoreboard players set #best timber.data -1
scoreboard players set #done timber.data 0
function timber:fall/try {off:3375}
function timber:fall/try {off:-3375}
execute if score #done timber.data matches 0 run function timber:fall/try {off:0}
execute if score #done timber.data matches 0 run function timber:fall/try {off:6750}
execute if score #done timber.data matches 0 run function timber:fall/try {off:-6750}
execute if score #done timber.data matches 0 run function timber:fall/try {off:9000}
execute if score #done timber.data matches 0 run function timber:fall/try {off:-9000}
scoreboard players set #drop timber.data 0
execute if score #best timber.data matches 13500.. run function timber:fall/drop
