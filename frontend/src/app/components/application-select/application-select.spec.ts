import { TestBed } from '@angular/core/testing';
import { ApplicationSelectComponent } from './application-select';
describe('ApplicationSelectComponent',()=>{
 it('filters full options and emits the selected system',()=>{
  const f=TestBed.createComponent(ApplicationSelectComponent);f.componentRef.setInput('options',[{systemId:'crm',name:'CRM Customers',summary:'Contact management'}]);
  let id='';f.componentInstance.selected.subscribe(v=>{id=v;f.componentRef.setInput('selectedId',v);});f.detectChanges();
  f.nativeElement.querySelector('button').click();f.detectChanges();
  f.componentInstance.search.set('contact');f.detectChanges();
  f.nativeElement.querySelector('[role=option]').click();expect(id).toBe('crm');expect(f.componentInstance.open()).toBe(false);
  f.detectChanges();
  expect(f.nativeElement.querySelector('.application-select__control strong').textContent).toBe('CRM Customers');
  expect(f.nativeElement.querySelector('.application-select__control small').textContent).toBe('Contact management');
 });
 it('keeps keyboard navigation through the search and application options',()=>{
  const f=TestBed.createComponent(ApplicationSelectComponent);
  f.componentRef.setInput('options',[{systemId:'crm-customers',name:'CRM Customers'},{systemId:'crm-contacts',name:'CRM Contacts'}]);
  f.detectChanges();f.nativeElement.querySelector('.application-select__control').click();f.detectChanges();
  const search=f.nativeElement.querySelector('input');search.focus();
  search.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));
  const options=f.nativeElement.querySelectorAll('[role=option]');
  expect(document.activeElement).toBe(options[0]);
  options[0].dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));
  expect(document.activeElement).toBe(options[1]);
  options[1].dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowUp',bubbles:true}));
  expect(document.activeElement).toBe(options[0]);
 });
});

