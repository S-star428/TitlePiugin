<h1 align="center"> S - TitlePiugin </h1>

<p align="left"> This plugin is a Minecraft plugin for setting player titles. </p>

<p> You can also use custom placeholders with the Placeholder API plugin. </p>

<p> The %star_title% placeholder can be used to display your set title on the scoreboard. </p>

<p> The %star_title_nametag% placeholder shows the equipped title on the name tag. </p>

<p> Supported versions: 1.20.1 ~ 1.21.3 </p> <p> It may not work on lower versions. Verified version: 1.20.1 </p>

<h2> Update Log </h2>

<p> v0.2: Updated to work with version 1.21.3. </p>

<p> v0.3: Modified to store both player UUID and player nickname in title.yml for easier identification. </p>
<p> v0.4: Gradient titles can now be created. Properly applied to scoreboard, chat, and tab list. </p>
<p>Title storage method has been changed.
Titles now appear next to the player's name above their head.</p>
<p>More customizable messages and a wider variety of sounds are now available.</p>
<p>To display the title next to the name above the player's head, you must use the TAB plugin and LuckPerms plugin.</p>

<p> v0.5: Added a name tag-specific placeholder. </p>

<h1> How to display titles next to the name above the player’s head </h1>
<p>First, create a group using the TAB plugin, then add a tagprefix to that group along with the title placeholder.</p>
default:<br>
  customtabname: '%player%'<br>
  customtagname: '%player%'<br>
  tagprefix: '%star_title% &f'


<p>====================================================================</p>

<p align="left"> 이 플러그인은 Minecraft 칭호를 설정하는 플러그인입니다. </p>

<p> 전용 플레이스홀더를 Placeholder API 플러그인으로 사용할 수도 있습니다. </p>

<p> %star_title% 이 플레이스홀더는 스코어보드에 내가 장착한 창호을 보여주는 데 사용할 수 있습니다. </p> 

<p> %star_title_nametag% 이 플레이스홀더는 네임태그에 내가 장착한 칭호를 보여줍니다. </p>

<p> 지원되는 버전: 1.20.1 ~ 1.21.3 </p> <p> 그 이하 버전에서는 작동하지 않을 수 있습니다. 검증된 버전: 1.20.1 </p> 

<h2> 업데이트 로그 </h2>

<p> v0.2 : 버전 1.21.3에서 작동하도록 수정되었습니다. </p>

<p> v0.3 : 칭호 저장 부분인 title.yml에 플레이어 UUID뿐만 아니라 플레이어 닉네임도 함께 저장하여 더 쉽게 알아볼 수 있도록 수정되었습니다. </p>
<p> v0.4: 그라데이션 칭호도 이제 제작을 통해 만들 수 있습니다. 스코어보드, 채팅, 탭리스트에도 그라데이션 칭호가 재대로 적용이 되었습니다.</p>
칭호 저장 방식이 변경되었습니다.
플레이어의 머리 위의 닉네임 옆에 칭호가 추가되어 나옵니다.</p>
<p>수정가능한 메세지, 소리가 더 다양해졌습니다.</p>
<p>플레이어 머리 위의 닉네임 옆에도 칭호를 나오게 하고 싶으면 탭 플러그인, 럭펌 플러그인이 필수로 필요합니다.</p>

<p> v0.5: 네임태그 전용 플레이스 홀더가 추가되었습니다.</p>

<h1> 플레이어의 머리 위의 닉네임 옆에도 칭호를 나오게 하는 방법</h1>
<p>먼저 탭 플러그인으로 그룹을 하나 만든 뒤 그 그룹의 tagprefix를 추가하고 그 옆에 칭호 플레이스 홀더를 추가합니다.</p>
default:<br>
  customtabname: '%player%'<br>
  customtagname: '%player%'<br>
  tagprefix: '%star_title% &f'
