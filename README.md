<h1 align="center"> S - TitlePiugin </h1>

<p align="left"> This plugin is a Minecraft plugin for setting player titles. </p>

<p> You can also use custom placeholders with the Placeholder API plugin. </p>

<p> The %star_title% placeholder can be used to display your set title on the scoreboard. </p>

<p> The %star_title_nametag% placeholder shows the equipped title on the name tag. </p>

<p> Supported versions: 1.20.1 ~ 1.21.3 </p> <p> It may not work on lower versions. Verified version: 1.20.1 </p>

<h2> Update Log </h2>

<p> v0.9: Updated to work with version 1.21.3. </p>

<p> v0.9a: Modified to store both player UUID and player nickname in title.yml for easier identification. </p>
<p> v1.0: Gradient titles can now be created. Properly applied to scoreboard, chat, and tab list. </p>
<p>Title storage method has been changed.
Titles now appear next to the player's name above their head.</p>
<p>More customizable messages and a wider variety of sounds are now available.</p>
<p>To display the title next to the name above the player's head, you must use the TAB plugin and LuckPerms plugin.</p>

<p> v1.1: Added a name tag-specific placeholder. </p>

<p> v1.2: All titles created by adding a list of titles can be viewed in the list, 
and holders and fitters can be seen directly from the list, We added the desired title from the list so that you can 
receive it right away, and if the title is deleted from the list, the title is deleted even when it is held or installed.</p>
<p> v1.3: Fixed a bug that allowed you to register a title if you owned a title book after deleting the title from the cardegori. In the title open, you
Added date and time when the title was obtained. /Initialize the entire title, /Initialize the title, /Initialize the title [Player Nickname] command, and the player concerned,
You can delete all titles of the entire player.</p>

<h1> How to display titles next to the name above the player’s head </h1>
<p>First, create a group using the TAB plugin, then add a tagprefix to that group along with the title placeholder.</p>
default:<br>
  customtabname: '%player%'<br>
  customtagname: '%player%'<br>
  tagprefix: '%star_title% &f'

<h1> Caution: Use the name tag placeholder and scoreboard placeholder separately </h1>


<p>====================================================================</p>

<p align="left"> 이 플러그인은 Minecraft 칭호를 설정하는 플러그인입니다. </p>

<p> 전용 플레이스홀더를 Placeholder API 플러그인으로 사용할 수도 있습니다. </p>

<p> %star_title% 이 플레이스홀더는 스코어보드에 내가 장착한 창호을 보여주는 데 사용할 수 있습니다. </p> 

<p> %star_title_nametag% 이 플레이스홀더는 네임태그에 내가 장착한 칭호를 보여줍니다. </p>

<p> 지원되는 버전: 1.20.1 ~ 1.21.3 </p> <p> 그 이하 버전에서는 작동하지 않을 수 있습니다. 검증된 버전: 1.20.1 </p> 

<h2> 업데이트 로그 </h2>

<p> v0.9 : 버전 1.21.3에서 작동하도록 수정되었습니다. </p>

<p> v0.9a: 칭호 저장 부분인 title.yml에 플레이어 UUID뿐만 아니라 플레이어 닉네임도 함께 저장하여 더 쉽게 알아볼 수 있도록 수정되었습니다. </p>
<p> v1.0: 그라데이션 칭호도 이제 제작을 통해 만들 수 있습니다. 스코어보드, 채팅, 탭리스트에도 그라데이션 칭호가 재대로 적용이 되었습니다.</p>
칭호 저장 방식이 변경되었습니다.
플레이어의 머리 위의 닉네임 옆에 칭호가 추가되어 나옵니다.</p>
<p>수정가능한 메세지, 소리가 더 다양해졌습니다.</p>
<p>플레이어 머리 위의 닉네임 옆에도 칭호를 나오게 하고 싶으면 탭 플러그인, 럭펌 플러그인이 필수로 필요합니다.</p>

<p> v1.1: 네임태그 전용 플레이스 홀더가 추가되었습니다.</p>
<p> v1.2: 칭호 목록을 추가하여 만든 모든 칭호를 목록에서 볼 수 있으며, 보유자와 장착자를 목록에서 바로 확인 할 수 있게 추가하였으며,
목록에서 원하는 칭호를 바로 지급받을 수 있게 추가하였으며, 목록에서 칭호를 삭제하면 보유하거나 장착한 상태에서도 칭호가 지워지도록 추가하였습니다.</p>
<p> v1.3: 칭호를 카데고리에서 삭제 후 칭호북을 소유하고 있을경우 칭호를 등록 할 수 있던 버그를 수정하였습니다. 칭호 열기에서 자신이 해당 
칭호를 언제 얻었는지 날짜와 시간을 추가하였습니다. /칭호 전체초기화, /칭호 초기화 [플레이어 닉네임] 명령어를 추가해서 해당 플레이어,
전체 플레이어의 모든 칭호를 삭제할 수 있습니다.</p>

<h1> 플레이어의 머리 위의 닉네임 옆에도 칭호를 나오게 하는 방법</h1>
<p>먼저 탭 플러그인으로 그룹을 하나 만든 뒤 그 그룹의 tagprefix를 추가하고 그 옆에 칭호 플레이스 홀더를 추가합니다.</p>
default:<br>
  customtabname: '%player%'<br>
  customtagname: '%player%'<br>
  tagprefix: '%star_title% &f'

<h1> 주의사항 : 네임태그 플레이스홀더랑 스코어보드 플레이스 홀더는 반드시 따로 사용하세요 </h1>