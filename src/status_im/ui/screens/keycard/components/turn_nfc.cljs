(ns  status-im.ui.screens.keycard.components.turn-nfc
  (:require [re-frame.core :as re-frame]
            [status-im.i18n :as i18n]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.components.react :as react]
            [status-im.ui.screens.keycard.components.animated-circles
             :refer [animated-circles]]
            [status-im.react-native.resources :as resources]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.button :as button]
            [status-im.ui.screens.keycard.components.style :as styles]))

(defn turn-nfc-on []
  [react/view {:style styles/wrapper-style}
   [react/view {:style styles/container-style}
    [vector-icons/icon :main-icons/union-nfc {:color  colors/blue
                                              :height 36
                                              :width  36}]
    [react/view {:margin-top 16}
     [react/text {:style {:typography :title-bold}}
      (i18n/label :t/turn-nfc-on)]]
    [react/view {:margin-top 8}
     [react/text {:number-of-lines 2
                  :style           styles/helper-text-style}
      (i18n/label :t/turn-nfc-description)]]

    [button/button {:label    :t/open-nfc-settings
                    :style    {:margin-top 16}
                    :on-press #(re-frame/dispatch [:keycard.onboarding.nfc-on/open-nfc-settings-pressed])}]]])
