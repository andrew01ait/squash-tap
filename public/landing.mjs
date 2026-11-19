import {mountLanguage, t} from './locale.mjs';
import {bindPageCopy} from './page-copy.mjs';
import {landingCopy} from './landing-copy.mjs';

const applyCopy = bindPageCopy(document, landingCopy);
function render() {
  applyCopy();
  const accent = text => {
    const element = document.createElement('em');
    element.textContent = text;
    return element;
  };
  document.getElementById('hero-title').replaceChildren(
    t('Keep ', 'Punkte ', 'Note le ', 'Lleva el '),
    accent(t('score.', 'zählen.', 'score.', 'marcador.')),
    document.createElement('br'),
    t('Keep ', 'Weiter', 'Reste en ', 'Sigue '),
    accent(t('playing.', 'spielen.', 'jeu.', 'jugando.'))
  );
}
mountLanguage(document.querySelector('[data-language-control]'), render);
render();
