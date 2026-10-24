package me.aap.fermata.addon.tv;

import android.view.View;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.FloatingButtonMediator;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
class TvFloatingButtonMediator extends FloatingButtonMediator {
	static final TvFloatingButtonMediator instance = new TvFloatingButtonMediator();

	/**
	 * TV ekranlarında kök sayfa, ana ekran kartından açıldığında da (aktif gezinme öğesi TV
	 * değilken) "kaynak ekle" düğmesi olarak kalmalı. Bu yüzden TV fragmentlerinde kökü
	 * fragmentin kendisi belirler; diğer ekranlarda eski davranış korunur.
	 */
	private static boolean showBack(MainActivityDelegate a) {
		if (a.isVideoMode()) return true;
		ActivityFragment f = a.getActiveFragment();
		if (f instanceof TvFragment) return !f.isRootPage();
		return !a.isRootPage();
	}

	@Override
	public int getIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		return showBack(a) ? getBackIcon() : R.drawable.tv_add;
	}

	@Override
	public void onClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());

		if (showBack(a)) {
			a.onBackPressed();
		} else {
			ActivityFragment f = a.getActiveFragment();
			if (f instanceof TvFragment) ((TvFragment) f).addSource();
		}
	}
}
